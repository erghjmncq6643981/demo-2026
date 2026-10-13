package com.chandler.fcc.server.call.domain;

import java.time.LocalDateTime;

/**
 * 对账允许提交的终态决策，区分真实结束事件与完整快照缺失证据。
 *
 * @param callId FCC 通话主键
 * @param version 检查证据时的通话版本
 * @param endedAt UTC 结束时间
 * @param cause 原始挂机原因或 RECOVERY_UNKNOWN
 * @param evidence 可审计的结束证据
 * @param channelsAbsent 完整快照是否确认所有关联话道不存在
 */
public record CallEndDecision(
    Long callId, Long version, LocalDateTime endedAt, String cause,
    String evidence, boolean channelsAbsent
) {}
