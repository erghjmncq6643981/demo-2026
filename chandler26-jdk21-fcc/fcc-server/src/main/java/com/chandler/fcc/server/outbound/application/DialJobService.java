package com.chandler.fcc.server.outbound.application;

import com.chandler.fcc.common.util.IdUtil;
import com.chandler.fcc.server.customer.domain.PhoneNumber;
import com.chandler.fcc.server.flow.FlowConfig;
import com.chandler.fcc.server.outbound.infrastructure.DialJobMapper;
import com.chandler.fcc.server.telephony.application.OutboundCallService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/**
 * 持久化自动外呼任务，并将数据库领取与网络拨号分离。
 *
 * <p>派发结果未知时由通话事实对账，调度器不会盲目重拨。</p>
 */
@Service
@RequiredArgsConstructor
@EnableScheduling
@Slf4j
public class DialJobService {

    private static final String TYPE_AUTO_FLOW = "AUTO_FLOW";
    private static final String TYPE_AGENT_CALLBACK = "AGENT_CALLBACK";
    private static final Set<String> AUTO_FLOW_MODEL_TYPES = Set.of(
        "NOTIFICATION",
        "AUTO_DIAL",
        "AUTO_DIAL_NOTIFICATION"
    );
    private static final String STATUS_FAILED = "FAILED";
    private static final int MAX_DAILY_NUMBER_ATTEMPTS = 3;

    private final DialJobMapper mapper;
    private final TransactionTemplate transactions;
    private final OutboundCallService calls;
    private final ObjectMapper objectMapper;
    private final FlowConfig flowConfig;

    @Value("${fcc.outbound.enabled:false}")
    private boolean enabled;

    @Value("${fcc.outbound.max-in-flight:5}")
    private int maxInFlight;

    @Value("${fcc.outbound.start-hour:9}")
    private int startHour;

    @Value("${fcc.outbound.end-hour:18}")
    private int endHour;

    @Value("${fcc.outbound.timezone:Asia/Shanghai}")
    private String timezone;

    /**
     * 创建与坐席无关的流程型自动外呼任务。
     *
     * <p>任务只拨打客户号码；只有流程执行到转人工节点时，运行时才动态路由坐席。</p>
     *
     * @param createdBy 创建人账号，仅用于审计
     * @param number 目标号码
     * @param flowKey 已发布自动外呼流程编码
     * @param variables 流程输入变量
     * @param maxAttempts 最大尝试次数
     * @param requestKey 业务幂等键
     * @return 任务标识
     */
    public String createAuto(
        String createdBy,
        String number,
        String flowKey,
        Map<String, Object> variables,
        int maxAttempts,
        String requestKey
    ) {
        validateAutoFlow(flowKey);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("number", PhoneNumber.normalize(number));
        payload.put("variables", variables == null ? Map.of() : variables);
        return createJob(
            null,
            createdBy,
            flowKey,
            TYPE_AUTO_FLOW,
            maxAttempts,
            requestKey,
            payload
        );
    }

    /**
     * 创建漏话回拨使用的坐席人工外呼任务。
     *
     * <p>该任务与无人自动外呼使用不同任务类型，调度时才校验指定坐席可发起外呼。</p>
     *
     * @param owner 执行回拨的坐席工号
     * @param number 客户号码
     * @param maxAttempts 最大尝试次数
     * @param requestKey 业务幂等键
     * @return 任务标识
     */
    public String createAgentCallback(
        String owner,
        String number,
        int maxAttempts,
        String requestKey
    ) {
        return createJob(
            owner,
            owner,
            null,
            TYPE_AGENT_CALLBACK,
            maxAttempts,
            requestKey,
            Map.of("number", PhoneNumber.normalize(number))
        );
    }

    /**
     * 持久化一种明确类型的外呼任务。
     *
     * @param owner 仅人工回拨任务使用的坐席工号
     * @param createdBy 创建人账号
     * @param flowKey 自动外呼流程编码
     * @param jobType 任务类型
     * @param maxAttempts 最大尝试次数
     * @param requestKey 业务幂等键
     * @param payload 任务载荷
     * @return 任务标识
     */
    private String createJob(
        String owner,
        String createdBy,
        String flowKey,
        String jobType,
        int maxAttempts,
        String requestKey,
        Map<String, Object> payload
    ) {
        validateCreate(maxAttempts, requestKey);
        if (createdBy == null || createdBy.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "任务创建人不能为空");
        }
        String id = String.valueOf(IdUtil.nextId());
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("owner", owner);
        row.put("createdBy", createdBy);
        row.put("flowKey", flowKey);
        row.put("key", requestKey);
        row.put("jobType", jobType);
        row.put("maxAttempts", maxAttempts);
        try {
            row.put("payload", objectMapper.writeValueAsString(payload));
            mapper.create(row);
        } catch (DuplicateKeyException failure) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该请求已创建，请刷新任务列表");
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("外呼任务序列化失败", failure);
        }
        log.info(
            "[外呼调度] 创建任务 jobId={} jobType={} flowKey={} createdBy={}",
            id,
            jobType,
            flowKey,
            createdBy
        );
        return id;
    }

    /**
     * 周期领取并派发一条任务，多实例通过数据库锁共享全局容量。
     */
    @Scheduled(fixedDelayString = "${fcc.outbound.poll-millis:2000}")
    public void dispatch() {
        if (!enabled) return;
        try {
            reconcile();
            if (!withinCallingWindow()) return;

            Map<String, Object> job = transactions.execute(status -> claimNext());
            if (job == null) return;
            dispatchClaimed(job);
        } catch (RuntimeException failure) {
            log.error("[自动外呼] 调度暂不可用 type={}", failure.getClass().getSimpleName(), failure);
        }
    }

    /**
     * 在事务内获取容量锁并创建一次外呼尝试。
     *
     * @return 已领取任务及尝试参数，无可执行任务时为空
     */
    private Map<String, Object> claimNext() {
        if (mapper.schedulerLock() == null) throw new IllegalStateException("缺少外呼调度锁基线");
        if (mapper.activeCount() >= maxInFlight) return null;

        Map<String, Object> row = mapper.next();
        if (row == null) return null;
        String id = row.get("id").toString();
        String number = row.get("number").toString();
        if (mapper.frequency(number) >= MAX_DAILY_NUMBER_ATTEMPTS) {
            mapper.finishJob(id, STATUS_FAILED);
            return null;
        }

        int attemptNo = mapper.countAttempts(id) + 1;
        if (attemptNo > ((Number) row.get("maxAttempts")).intValue()) {
            mapper.finishJob(id, STATUS_FAILED);
            return null;
        }
        if (mapper.claim(id) != 1) return null;

        row.put("attempt", String.valueOf(IdUtil.nextId()));
        row.put("attemptNo", attemptNo);
        mapper.startAttempt(row);
        return row;
    }

    /**
     * 对已持久化的尝试产生一次拨号副作用。
     *
     * @param job 已领取任务
     */
    private void dispatchClaimed(Map<String, Object> job) {
        String attemptId = job.get("attempt").toString();
        try {
            Map<String, Object> result = TYPE_AUTO_FLOW.equals(job.get("jobType"))
                ? calls.startAutoDial(
                    job.get("number").toString(),
                    attemptId,
                    job.get("flowKey").toString(),
                    readVariables(job.get("variables"))
                )
                : calls.startFor(
                    job.get("owner").toString(),
                    job.get("number").toString(),
                    attemptId
                );
            mapper.attach(attemptId, result.get("callId").toString());
        } catch (RuntimeException failure) {
            if (isUnknownDispatch(failure)) {
                log.warn("[自动外呼] 派发结果未知，保留尝试等待对账 attemptId={}", attemptId);
                return;
            }
            handleUnconfirmedDispatch(job.get("id").toString(), attemptId);
            log.warn("[自动外呼] 派发未确认 attemptId={}", attemptId);
        }
    }

    /**
     * 仅在确定没有持久化通话意图时结束失败尝试。
     *
     * @param jobId 任务标识
     * @param attemptId 尝试标识
     */
    private void handleUnconfirmedDispatch(String jobId, String attemptId) {
        if (mapper.call(attemptId) != null) return;
        transactions.executeWithoutResult(status -> {
            if (mapper.finishAttempt(attemptId, STATUS_FAILED, "PRECONDITION_FAILED") == 1) {
                mapper.finishJob(jobId, STATUS_FAILED);
            }
        });
    }

    /**
     * 根据通话事实回填运行中尝试，并结束没有通话意图的过期派发。
     */
    private void reconcile() {
        List<String> expired = mapper.expiredDispatches();
        if (!expired.isEmpty()) mapper.expireDispatches(expired);

        mapper.unattached().forEach(row -> mapper.attach(
            row.get("id").toString(),
            row.get("callId").toString()
        ));

        List<String> running = mapper.running()
            .stream()
            .map(row -> row.get("id").toString())
            .distinct()
            .toList();
        if (!running.isEmpty()) mapper.reconcileBatch(running);
    }

    /**
     * 判断异常是否表示 Sidecar 已经可能执行但 FCC 未收到确认。
     *
     * @param failure 派发异常
     * @return 结果未知时返回 {@code true}
     */
    private boolean isUnknownDispatch(RuntimeException failure) {
        return failure instanceof ResponseStatusException response &&
            response.getStatusCode().value() == HttpStatus.GATEWAY_TIMEOUT.value();
    }

    /**
     * 判断当前时间是否处于允许拨号的本地时间窗口。
     *
     * @return 可以拨号时返回 {@code true}
     */
    private boolean withinCallingWindow() {
        if (startHour < 0 || endHour > 24 || startHour >= endHour) return false;
        int hour = ZonedDateTime.now(ZoneId.of(timezone)).getHour();
        return hour >= startHour && hour < endHour;
    }

    /**
     * 校验任务创建参数。
     *
     * @param maxAttempts 最大尝试次数
     * @param requestKey 业务幂等键
     */
    private void validateCreate(int maxAttempts, String requestKey) {
        if (
            maxAttempts < 1 ||
            maxAttempts > 3 ||
            requestKey == null ||
            !requestKey.matches("[A-Za-z0-9_-]{8,100}")
        ) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "尝试次数或请求标识不合法");
        }
    }

    /**
     * 校验自动外呼流程存在已发布版本且类型正确。
     *
     * @param flowKey 流程编码
     */
    private void validateAutoFlow(String flowKey) {
        if (flowKey == null || !flowKey.matches("[A-Za-z][A-Za-z0-9_-]{0,63}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "自动外呼流程编码无效");
        }
        FlowConfig.FlowSnapshot flow = flowConfig.getPublishedFlow(null, flowKey)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "自动外呼流程尚未发布"));
        String modelType = flow.modelType() == null ? "" : flow.modelType().toUpperCase();
        if (!AUTO_FLOW_MODEL_TYPES.contains(modelType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "所选流程不是自动外呼流程");
        }
    }

    /**
     * 将数据库 JSON 变量转换为运行参数。
     *
     * @param value JSON 文本或映射
     * @return 非空流程变量
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> readVariables(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        if (value == null || value.toString().isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(value.toString(), Map.class);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("自动外呼流程变量不是有效 JSON", failure);
        }
    }
}
