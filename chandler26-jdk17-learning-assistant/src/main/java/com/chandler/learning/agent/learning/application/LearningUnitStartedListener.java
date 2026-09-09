package com.chandler.learning.agent.learning.application;

import com.chandler.learning.agent.identity.application.UserDisplayNameService;
import com.chandler.learning.agent.system.application.SystemLogService;
import com.chandler.learning.agent.system.domain.enums.SystemLogType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 事务提交后异步记录学习单元切换审计日志。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LearningUnitStartedListener {

    private final SystemLogService systemLogService;
    private final UserDisplayNameService userDisplayNameService;

    /** 状态已由主事务提交，监听器只负责不影响业务结果的审计日志。 */
    @Async("learningEventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUnitStarted(LearningUnitStartedEvent event) {
        try {
            // 状态已经在主事务中提交，日志失败不能回滚学习事实。
            systemLogService.record(event.userId(), SystemLogType.LEARNING_PLAN, "切换场景学习单元",
                    event.planName() + " / " + event.unitTitle());
            log.info("用户「{}」开始学习计划「{}」中的场景「{}」",
                    userDisplayNameService.userName(event.userId()), event.planName(), event.unitTitle());
        } catch (RuntimeException ex) {
            log.warn("event=unit_started_async_audit result=failed planId={} unitId={} errorType={}",
                    event.planId(), event.unitId(), ex.getClass().getSimpleName());
            log.debug("异步记录场景学习单元审计日志失败 planId={} unitId={}", event.planId(), event.unitId(), ex);
        }
    }
}
