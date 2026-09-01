package com.chandler.learning.agent.learning.application;

import com.chandler.learning.agent.learning.infrastructure.mapper.LearningReviewRecordMapper;
import com.chandler.learning.agent.system.application.SystemLogService;
import com.chandler.learning.agent.system.domain.enums.SystemLogType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 异步持久化答题历史流水与审计日志，从词汇检查接口主线程解耦。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LearningAssessmentSubmittedListener {

    private final LearningReviewRecordMapper reviewRecordMapper;
    private final SystemLogService systemLogService;

    /** 事务提交后异步落库流水与日志。 */
    @Async("learningEventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAssessmentSubmitted(LearningAssessmentSubmittedEvent event) {
        try {
            // 1. 异步持久化答题详细流水
            if (event.record() != null) {
                reviewRecordMapper.insert(event.record());
            }

            // 2. 异步记录用户审计日志
            systemLogService.record(
                    event.userId(),
                    SystemLogType.REVIEW,
                    "提交词汇检查结果",
                    event.term() + " -> " + event.resultLabel());

            // 3. 输出结构化业务日志
            log.info("用户「{}」完成了单词「{}」的场景检查，结果是「{}」，熟练度从 {} 提升到 {}",
                    event.userName(),
                    event.term(),
                    event.resultLabel(),
                    event.masteryBefore(),
                    event.masteryAfter());
        } catch (RuntimeException ex) {
            log.error("event=assessment_submitted_async_persist result=failed userId={} term={} error={}",
                    event.userId(), event.term(), ex.getMessage(), ex);
        }
    }
}
