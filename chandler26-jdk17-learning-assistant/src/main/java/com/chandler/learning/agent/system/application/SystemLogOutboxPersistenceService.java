package com.chandler.learning.agent.system.application;

import com.chandler.learning.agent.system.domain.entity.LearningSystemLog;
import com.chandler.learning.agent.system.domain.entity.LearningSystemLogOutbox;
import com.chandler.learning.agent.system.infrastructure.mapper.LearningSystemLogMapper;
import com.chandler.learning.agent.system.infrastructure.mapper.LearningSystemLogOutboxMapper;
import com.chandler.learning.agent.common.constant.CommonConstants;
import com.chandler.learning.agent.common.constant.PersistenceConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 系统日志 Outbox 的批量、幂等投递服务。
 * <p>
 * 领取、最终日志批量写入和成功标记处于一个短事务中；事务回滚后 Outbox 会保持待处理，供恢复调度器再次投递。
 */
@Service
@RequiredArgsConstructor
public class SystemLogOutboxPersistenceService {

    private final LearningSystemLogOutboxMapper systemLogOutboxMapper;
    private final LearningSystemLogMapper systemLogMapper;

    /** 处理提交后事件指定的 Outbox 记录。 */
    @Transactional(rollbackFor = Exception.class)
    public void persistByIds(List<Long> outboxIds) {
        if (outboxIds == null || outboxIds.isEmpty()) {
            return;
        }
        String claimToken = newClaimToken();
        if (systemLogOutboxMapper.claimByIds(outboxIds, claimToken) > CommonConstants.ZERO) {
            persistClaimed(claimToken);
        }
    }

    /** 恢复因进程重启或线程池拥塞而未被提交后监听器消费的待处理事件。 */
    @Transactional(rollbackFor = Exception.class)
    public int persistPendingBatch(int limit) {
        String claimToken = newClaimToken();
        if (systemLogOutboxMapper.claimPendingBatch(claimToken, limit) <= CommonConstants.ZERO) {
            return CommonConstants.ZERO;
        }
        return persistClaimed(claimToken);
    }

    private int persistClaimed(String claimToken) {
        List<LearningSystemLogOutbox> outboxRows = systemLogOutboxMapper.selectByClaimToken(claimToken);
        if (outboxRows.isEmpty()) {
            return CommonConstants.ZERO;
        }
        systemLogMapper.insertBatch(outboxRows.stream().map(this::toSystemLog).toList());
        systemLogOutboxMapper.markSucceededByClaimToken(claimToken);
        return outboxRows.size();
    }

    private LearningSystemLog toSystemLog(LearningSystemLogOutbox outbox) {
        LearningSystemLog log = new LearningSystemLog();
        log.setId(outbox.getId());
        log.setUserId(outbox.getUserId());
        log.setLogType(outbox.getLogType());
        log.setTitle(outbox.getTitle());
        log.setDetail(outbox.getDetail());
        log.setSource(outbox.getSource());
        log.setBusinessType(outbox.getBusinessType());
        log.setBusinessId(outbox.getBusinessId());
        log.setCreateBy(outbox.getCreateBy());
        log.setUpdateBy(outbox.getUpdateBy());
        log.setCreateTime(outbox.getOccurredAt());
        log.setUpdateTime(outbox.getUpdateTime() == null ? LocalDateTime.now() : outbox.getUpdateTime());
        log.setDeleted(false);
        log.setVersion(PersistenceConstants.INITIAL_VERSION);
        return log;
    }

    private String newClaimToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
