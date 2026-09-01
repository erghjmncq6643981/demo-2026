package com.chandler.learning.agent.learning.application;

import com.chandler.learning.agent.learning.domain.entity.LearningReviewRecord;

/**
 * 词汇检查提交完成事件，用于在事务提交后异步持久化答题流水、系统日志与输出日志。
 */
public record LearningAssessmentSubmittedEvent(
        Long userId,
        LearningReviewRecord record,
        String planName,
        String unitTitle,
        String term,
        String resultLabel,
        Integer masteryBefore,
        Integer masteryAfter,
        String userName
) {}
