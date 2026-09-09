package com.chandler.learning.agent.learning.application;

import com.chandler.learning.agent.system.application.SystemLogService;
import com.chandler.learning.agent.system.domain.enums.SystemLogType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 事务提交后异步记录答题审计日志；答题流水已在主事务内持久化，避免学习记录丢失。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LearningAssessmentSubmittedListener {

    private final SystemLogService systemLogService;

    /** 事务提交后异步落库流水与日志。 */
    @Async("learningEventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAssessmentSubmitted(LearningAssessmentSubmittedEvent event) {
        try {
            // 审计日志不影响已经提交的学习状态和答题事实记录。
            systemLogService.record(
                    event.userId(),
                    SystemLogType.REVIEW,
                    "提交词汇检查结果",
                    event.term() + " -> " + event.resultLabel());

            log.info("用户「{}」完成了单词「{}」的场景检查，结果是「{}」，熟练度从 {} 提升到 {}",
                    event.userName(),
                    event.term(),
                    event.resultLabel(),
                    event.masteryBefore(),
                    event.masteryAfter());
        } catch (RuntimeException ex) {
            log.warn("event=assessment_submitted_async_audit result=failed userId={} term={} errorType={}",
                    event.userId(), event.term(), ex.getClass().getSimpleName());
            log.debug("异步记录答题审计日志失败 userId={} term={}", event.userId(), event.term(), ex);
        }
    }
}
