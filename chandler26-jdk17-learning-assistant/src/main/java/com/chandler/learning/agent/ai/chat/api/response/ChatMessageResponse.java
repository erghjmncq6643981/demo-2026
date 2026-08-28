package com.chandler.learning.agent.ai.chat.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对话消息响应。
 */
@Data
public class ChatMessageResponse {

    @Schema(description = "主键标识")
    private Long id;

    @Schema(description = "AI 会话标识")
    private Long sessionId;

    @Schema(description = "角色")
    private String role;

    @Schema(description = "内容")
    private String content;

    @Schema(description = "数量")
    private Integer tokenCount;

    @Schema(description = "耗时（毫秒）")
    private Long costTime;

    @Schema(description = "模型供应商")
    private String modelProvider;

    @Schema(description = "模型名称")
    private String modelName;

    @Schema(description = "排序序号")
    private Integer sequence;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
