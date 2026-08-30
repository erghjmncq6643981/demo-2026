package com.chandler.learning.agent.system.application;

import com.chandler.learning.agent.common.constant.CommonConstants;
import com.chandler.learning.agent.system.domain.constant.SystemLogConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 定期恢复尚未写入最终系统日志表的 Outbox 事件。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SystemLogOutboxRecoveryScheduler {

    private final SystemLogOutboxPersistenceService outboxPersistenceService;

    /** 恢复尚未消费的系统日志事件。 */
    @Scheduled(fixedDelayString = "${learning.audit-log.recovery-delay-ms:30000}")
    public void recoverPendingLogs() {
        try {
            int persisted = outboxPersistenceService.persistPendingBatch(SystemLogConstants.OUTBOX_BATCH_SIZE);
            if (persisted > CommonConstants.ZERO) {
                log.info("event=system_log_recovery result=success count={}", persisted);
            }
        } catch (RuntimeException ex) {
            log.warn("event=system_log_recovery result=failed error={}", ex.getClass().getSimpleName());
            log.debug("系统日志 Outbox 恢复失败", ex);
        }
    }
}
