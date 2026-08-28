package com.chandler.learning.agent.learning.application;

import java.time.LocalDateTime;

/**
 * 学习单元启动与切换事件，用于将状态持久化和审计日志从接口主线程解耦。
 */
public record LearningUnitStartedEvent(
        Long userId,
        Long planId,
        Long unitId,
        Long previousUnitId,
        boolean firstStart,
        LocalDateTime startedTime,
        String planName,
        String unitTitle,
        String traceId
) {}
