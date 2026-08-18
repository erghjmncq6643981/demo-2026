package com.chandler.learning.agent.service.vocabulary;

/** 词卡任务事务提交后触发的异步处理事件。 */
public record VocabularyCardGenerationRequestedEvent(Long userId, Long jobId, Long modelConfigId) {
}
