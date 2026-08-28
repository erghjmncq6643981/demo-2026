package com.chandler.learning.agent.system.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 在业务事务提交后异步保存产品内系统日志。
 * <p>
 * 日志入库失败只影响审计可见性，绝不能把已经完成的学习、管理或 AI 业务改成失败。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SystemLogPersistenceListener {

    private final SystemLogOutboxPersistenceService outboxPersistenceService;

    /**
     * 处理已提交事务的系统日志事件；非事务调用使用 fallbackExecution 立即异步处理。
     */
    @Async("auditLogExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void persist(SystemLogRecordedEvent event) {
        try {
            outboxPersistenceService.persistByIds(java.util.List.of(event.outboxId()));
            log.debug("event=system_log_persistence result=success outboxId={} userId={} type={} traceId={}",
                    event.outboxId(),
                    event.userId(), event.logType(), event.traceId());
        } catch (RuntimeException ex) {
            log.warn("event=system_log_persistence result=failed outboxId={} userId={} type={} traceId={} error={}",
                    event.outboxId(), event.userId(), event.logType(), event.traceId(), ex.getClass().getSimpleName());
            log.debug("系统日志异步入库失败 outboxId={} userId={} type={}",
                    event.outboxId(), event.userId(), event.logType(), ex);
        }
    }
}
