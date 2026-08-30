package com.chandler.learning.agent.vocabulary.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 事务提交后异步执行词卡 AI 生成，避免请求线程和数据库事务等待模型响应。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VocabularyCardGenerationWorker {

    private final VocabularyCardBatchService cardBatchService;

    /** 消费词卡生成事件并执行批量生成。 */
    @Async("aiTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(VocabularyCardGenerationRequestedEvent event) {
        try {
            cardBatchService.executeJob(event.userId(), event.jobId(), event.modelConfigId());
        } catch (RuntimeException ex) {
            log.error("event=vocabulary_card_job result=failed jobId={} error={}", event.jobId(), ex.getMessage());
            log.debug("异步词卡任务执行失败 jobId={}", event.jobId(), ex);
        }
    }
}
