package com.chandler.fcc.server.outbound.application;

import com.chandler.fcc.common.enums.AutoDialTaskType;
import com.chandler.fcc.common.enums.AutoDialTriggerSource;
import com.chandler.fcc.common.util.IdUtil;
import com.chandler.fcc.server.customer.domain.PhoneNumber;
import com.chandler.fcc.server.outbound.infrastructure.DialJobMapper;
import com.chandler.fcc.server.telephony.application.OutboundCallService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private static final String SYSTEM_NOTIFICATION = "SYSTEM_NOTIFICATION";
    private static final String STATUS_FAILED = "FAILED";
    private static final int MAX_DAILY_NUMBER_ATTEMPTS = 3;

    private final DialJobMapper mapper;
    private final TransactionTemplate transactions;
    private final OutboundCallService calls;
    private final ObjectMapper objectMapper;

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
     * <p>任务只拨打客户号码，接通后使用固定通知模型播放本次任务文案并收取确认按键。</p>
     *
     * @param createdBy 创建人账号，仅用于审计
     * @param number 目标号码
     * @param text 本次通知文案
     * @param confirmDigit 确认按键
     * @param timeoutSeconds 确认等待秒数
     * @param maxAttempts 最大尝试次数
     * @param requestKey 业务幂等键
     * @param bizId 可选业务关联标识
     * @return 任务标识
     */
    public String createAuto(
        String createdBy,
        String number,
        String text,
        String confirmDigit,
        int timeoutSeconds,
        int maxAttempts,
        String requestKey,
        String bizId
    ) {
        String notificationText = validateNotification(text, confirmDigit, timeoutSeconds);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("number", PhoneNumber.normalize(number));
        payload.put("text", notificationText);
        payload.put("confirmDigit", confirmDigit);
        payload.put("timeoutSeconds", timeoutSeconds);
        return createJob(
            null,
            createdBy,
            SYSTEM_NOTIFICATION,
            TYPE_AUTO_FLOW,
            AutoDialTaskType.NOTIFY.name(),
            AutoDialTriggerSource.FRONTEND.name(),
            normalizeBizId(bizId),
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
            null,
            null,
            null,
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
     * @param taskType 自动外呼业务类型
     * @param triggerSource 自动外呼触发来源
     * @param bizId 可选业务关联标识
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
        String taskType,
        String triggerSource,
        String bizId,
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
        row.put("taskType", taskType);
        row.put("triggerSource", triggerSource);
        row.put("bizId", bizId);
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
            "[外呼调度] 创建任务 jobId={} jobType={} taskType={} triggerSource={} createdBy={}",
            id,
            jobType,
            taskType,
            triggerSource,
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
                    job.get("text").toString(),
                    job.get("confirmDigit").toString(),
                    ((Number) job.get("timeoutSeconds")).intValue()
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
     * 校验并规范化固定通知模型所需的本次任务参数。
     *
     * @param text 通知文案
     * @param confirmDigit 确认按键
     * @param timeoutSeconds 确认等待秒数
     * @return 去除首尾空白后的通知文案
     */
    private String validateNotification(String text, String confirmDigit, int timeoutSeconds) {
        String normalized = text == null ? "" : text.trim();
        if (normalized.isEmpty() || normalized.length() > 1000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "通知文案不能为空且不能超过1000个字符");
        }
        if (confirmDigit == null || !confirmDigit.matches("[0-9]")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "确认按键必须是一位数字");
        }
        if (timeoutSeconds < 3 || timeoutSeconds > 60) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "确认等待时间必须为3至60秒");
        }
        return normalized;
    }

    /**
     * 规范可选业务关联标识。
     *
     * @param bizId 调用方业务标识
     * @return 空值或规范化业务标识
     */
    private String normalizeBizId(String bizId) {
        if (bizId == null || bizId.isBlank()) {
            return null;
        }
        String normalized = bizId.trim();
        if (normalized.length() > 128) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "业务标识不能超过128个字符");
        }
        return normalized;
    }
}
