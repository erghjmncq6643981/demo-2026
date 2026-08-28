package com.chandler.learning.agent.ai.chat.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Data;

import java.time.LocalDateTime;

/** 系统管理中的 AI 会话摘要。 */
@Data
public class AdminAiSessionResponse {

    @Schema(description = "主键标识")
    private Long id;
    @Schema(description = "用户标识")
    private Long userId;
    @Schema(description = "用户名")
    private String userName;
    @Schema(description = "标题")
    private String title;
    @Schema(description = "Agent 编码")
    private String agentCode;
    @Schema(description = "业务类型")
    private String businessType;
    @Schema(description = "业务标识")
    private String businessId;
    @Schema(description = "场景编码")
    private String sceneCode;
    @Schema(description = "数量")
    private Integer messageCount;
    @Schema(description = "调用次数")
    private Integer callCount;
    @Schema(description = "成功数量")
    private Integer successCount;
    @Schema(description = "失败数量")
    private Integer failedCount;
    @Schema(description = "总 Token 数")
    private Long totalTokens;
    @Schema(description = "平均耗时（毫秒）")
    private Long averageLatencyMs;
    @Schema(description = "最近供应商")
    private String lastProvider;
    @Schema(description = "名称")
    private String lastModelName;
    @Schema(description = "时间")
    private LocalDateTime createTime;
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
