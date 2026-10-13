package com.chandler.fcc.server.event.controller.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

/** 运维异常摘要；不包含原始事件、电话号码或身份令牌。 */
@Data
@Schema(description = "话务事件异常摘要")
public class EventRecoveryIssueResp {
    @Schema(description = "源事件唯一标识，64 位十六进制摘要")
    private String eventId;
    @Schema(description = "源 FreeSWITCH 节点标识")
    private String nodeId;
    @Schema(description = "异常处理状态，例如 UNKNOWN、RECOVERING")
    private String status;
    @Schema(description = "原事件消费尝试次数")
    private Integer attemptCount;
    @Schema(description = "人工事实修复尝试次数，最多十次")
    private Integer recoveryAttemptCount;
    @Schema(description = "最近一次修复的管理账号")
    private String recoveryActor;
    @Schema(description = "脱敏异常分类，不包含载荷内容")
    private String lastError;
    @Schema(description = "最近状态变更的 UTC 时间")
    private LocalDateTime updatedAt;
}
