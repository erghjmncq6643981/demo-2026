package com.chandler.learning.agent.system.application;

import java.time.LocalDateTime;

/**
 * 已完成业务动作后待写入产品内系统日志的不可变事件。
 * <p>
 * 事件在发布时完成用户、来源和文本边界的归一化，异步线程无需再读取安全上下文或请求对象。
 */
public record SystemLogRecordedEvent(
        Long outboxId,
        Long userId,
        String logType,
        String title,
        String detail,
        String source,
        String businessType,
        String businessId,
        LocalDateTime occurredAt,
        String traceId) {
}
