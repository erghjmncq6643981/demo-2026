package com.chandler.fcc.server.outbound.application;

import com.chandler.fcc.common.util.IdUtil;
import com.chandler.fcc.server.customer.domain.PhoneNumber;
import com.chandler.fcc.server.outbound.infrastructure.DialJobMapper;
import com.chandler.fcc.server.telephony.application.AgentIdentityService;
import com.chandler.fcc.server.telephony.application.OutboundCallService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.ZoneId;
import java.time.ZonedDateTime;
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

    private static final Set<String> SUPPORTED_MODES = Set.of("PROGRESSIVE", "NOTIFICATION");
    private static final String MODE_NOTIFICATION = "NOTIFICATION";
    private static final String STATUS_FAILED = "FAILED";
    private static final int MAX_DAILY_NUMBER_ATTEMPTS = 3;

    private final DialJobMapper mapper;
    private final AgentIdentityService identity;
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
     * 为当前登录坐席创建自动外呼任务。
     *
     * @param number 目标号码
     * @param mode 外呼模式
     * @param maxAttempts 最大尝试次数
     * @param requestKey 业务幂等键
     * @return 任务标识
     */
    public String create(String number, String mode, int maxAttempts, String requestKey) {
        var actor = identity.requirePrincipal();
        return createFor(actor.workNo(), number, mode, maxAttempts, requestKey);
    }

    /**
     * 在调用方完成身份校验后，为指定坐席创建自动外呼任务。
     *
     * <p>本方法只持久化任务，不产生网络副作用，因此可以加入调用方事务。</p>
     *
     * @param owner 负责坐席工号
     * @param number 目标号码
     * @param mode 外呼模式
     * @param maxAttempts 最大尝试次数
     * @param requestKey 业务幂等键
     * @return 任务标识
     */
    public String createFor(
        String owner,
        String number,
        String mode,
        int maxAttempts,
        String requestKey
    ) {
        validateCreate(mode, maxAttempts, requestKey);
        String id = String.valueOf(IdUtil.nextId());
        try {
            mapper.create(
                Map.of(
                    "id",
                    id,
                    "owner",
                    owner,
                    "key",
                    requestKey,
                    "mode",
                    mode,
                    "maxAttempts",
                    maxAttempts,
                    "payload",
                    objectMapper.writeValueAsString(Map.of("number", PhoneNumber.normalize(number)))
                )
            );
        } catch (DuplicateKeyException failure) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该请求已创建，请刷新任务列表");
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("外呼任务序列化失败", failure);
        }
        log.info("[自动外呼] 创建任务 workNo={} jobId={} mode={}", owner, id, mode);
        return id;
    }

    /**
     * 分页查询当前坐席的任务。
     *
     * @param page 页码
     * @return 任务摘要
     */
    public List<Map<String, Object>> list(int page) {
        var actor = identity.requirePrincipal();
        validatePage(page);
        return mapper.list(actor.workNo(), (page - 1) * 50);
    }

    /**
     * 查询当前坐席有权访问的任务及逐次结果。
     *
     * @param id 任务标识
     * @return 任务详情
     */
    public Map<String, Object> detail(String id) {
        validateId(id);
        var actor = identity.requirePrincipal();
        Map<String, Object> row = mapper.detail(actor.workNo(), id);
        if (row == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在");
        row.put("attempts", mapper.attempts(id));
        return row;
    }

    /**
     * 暂停、恢复或取消当前坐席的任务。
     *
     * <p>取消任务不会强制挂断已经开始的通话。</p>
     *
     * @param id 任务标识
     * @param action 操作代码
     */
    public void control(String id, String action) {
        validateId(id);
        var actor = identity.requirePrincipal();
        if (mapper.control(actor.workNo(), id, action) != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "当前任务不允许该操作");
        }
        log.info("[自动外呼] 任务操作 jobId={} action={} workNo={}", id, action, actor.workNo());
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
            Map<String, Object> result = MODE_NOTIFICATION.equals(job.get("mode"))
                ? calls.startNotificationFor(
                    job.get("owner").toString(),
                    job.get("number").toString(),
                    attemptId
                )
                : calls.startFor(
                    job.get("owner").toString(),
                    job.get("number").toString(),
                    attemptId
                );
            mapper.attach(attemptId, result.get("callId").toString());
        } catch (RuntimeException failure) {
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

        List<String> running = mapper.running()
            .stream()
            .map(row -> row.get("id").toString())
            .distinct()
            .toList();
        if (!running.isEmpty()) mapper.reconcileBatch(running);
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
     * @param mode 外呼模式
     * @param maxAttempts 最大尝试次数
     * @param requestKey 业务幂等键
     */
    private void validateCreate(String mode, int maxAttempts, String requestKey) {
        if (
            !SUPPORTED_MODES.contains(mode) ||
            maxAttempts < 1 ||
            maxAttempts > 3 ||
            requestKey == null ||
            !requestKey.matches("[A-Za-z0-9_-]{8,100}")
        ) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "外呼模式、次数或请求标识不合法");
        }
    }

    /**
     * 校验分页范围。
     *
     * @param page 页码
     */
    private void validatePage(int page) {
        if (page < 1 || page > 10000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "页码无效");
        }
    }

    /**
     * 验证雪花标识的公开字符串形式。
     *
     * @param id 待验证标识
     */
    private void validateId(String id) {
        if (id == null || !id.matches("[1-9][0-9]{0,18}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "任务标识无效");
        }
    }
}
