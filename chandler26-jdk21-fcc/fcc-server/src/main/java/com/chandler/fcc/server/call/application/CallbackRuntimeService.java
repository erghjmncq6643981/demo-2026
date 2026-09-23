package com.chandler.fcc.server.call.application;

import com.chandler.fcc.server.call.infrastructure.CallbackRuntimeMapper;
import com.chandler.fcc.server.outbound.application.DialJobService;
import com.chandler.fcc.server.telephony.application.AgentIdentityService;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/**
 * 负责坐席回拨任务的查询、排他领取和调度创建。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CallbackRuntimeService {

    private static final Set<String> ACTIVE_JOB_STATUSES = Set.of("PENDING", "RUNNING", "PAUSED");
    private static final Set<String> RETRYABLE_JOB_STATUSES = Set.of("FAILED", "CANCELLED");
    private static final Set<String> SCHEDULABLE_CALLBACK_STATUSES = Set.of("PENDING", "ASSIGNED");

    private final CallbackRuntimeMapper mapper;
    private final AgentIdentityService identity;
    private final DialJobService jobs;
    private final TransactionTemplate transactions;

    /**
     * 分页查询当前坐席可领取或已经领取的回拨任务。
     *
     * @param page 页码
     * @param number 精确号码筛选，可为空
     * @return 分页摘要
     */
    public Map<String, Object> list(int page, String number) {
        var actor = identity.requirePrincipal();
        if (page < 1 || page > 10000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "页码无效");
        }
        return Map.of(
            "pageNum",
            page,
            "pageSize",
            10,
            "total",
            mapper.count(actor.workNo(), number),
            "list",
            mapper.list(actor.workNo(), number, (page - 1) * 10)
        );
    }

    /**
     * 原子领取回拨记录并创建渐进式外呼任务。
     *
     * @param id 回拨记录标识
     * @return 已存在或新创建的外呼任务标识
     */
    public String schedule(String id) {
        validateId(id);
        var actor = identity.requirePrincipal();
        String jobId = transactions.execute(status -> scheduleLocked(actor.workNo(), id));
        log.info("[漏话回拨] 已关联调度 workNo={} callbackId={} jobId={}", actor.workNo(), id, jobId);
        return jobId;
    }

    /**
     * 在事务内锁定回拨记录并完成调度关联。
     *
     * @param owner 当前坐席工号
     * @param callbackId 回拨记录标识
     * @return 外呼任务标识
     */
    private String scheduleLocked(String owner, String callbackId) {
        Map<String, Object> row = mapper.lock(owner, callbackId);
        if (row == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "回拨已由其他坐席领取或不存在");
        }

        if (row.get("jobId") != null) {
            String status = String.valueOf(row.get("jobStatus"));
            if (ACTIVE_JOB_STATUSES.contains(status)) return row.get("jobId").toString();
            if (!RETRYABLE_JOB_STATUSES.contains(status)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "回拨已完成或结果尚未确认");
            }
        } else if (!SCHEDULABLE_CALLBACK_STATUSES.contains(String.valueOf(row.get("status")))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "当前回拨不能执行");
        }

        int attempt = ((Number) row.get("attempts")).intValue() + 1;
        String requestKey = "callback-" + callbackId + "-" + attempt;
        String jobId = jobs.createAgentCallback(
            owner,
            row.get("number").toString(),
            1,
            requestKey
        );
        if (mapper.schedule(owner, callbackId, jobId) != 1) {
            throw new IllegalStateException("回拨领取冲突");
        }
        return jobId;
    }

    /**
     * 验证雪花标识的公开字符串形式。
     *
     * @param id 待验证标识
     */
    private void validateId(String id) {
        if (id == null || !id.matches("[1-9][0-9]{0,18}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "回拨标识无效");
        }
    }
}
