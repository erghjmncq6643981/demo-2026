package com.chandler.learning.agent.ai.chat.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对话会话响应。
 */
@Data
public class ChatSessionResponse {

    /** 会话主键。 */
    @Schema(description = "主键 ID")
    private Long id;

    /** 会话所属用户 ID。 */
    @Schema(description = "用户标识")
    private Long userId;

    /** Agent 编码。 */
    @Schema(description = "Agent 编码")
    private String agentCode;

    /** 业务类型，例如 learning。 */
    @Schema(description = "业务类型")
    private String businessType;

    /** 业务对象 ID。 */
    @Schema(description = "业务标识")
    private String businessId;

    /** 可复用的学习场景编码。 */
    @Schema(description = "场景编码")
    private String sceneCode;

    /** 会话展示标题。 */
    @Schema(description = "标题")
    private String title;

    /** 当前会话的有效消息数量。 */
    @Schema(description = "消息数量")
    private Integer messageCount;

    /** 创建时间。 */
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    /** 最近更新时间。 */
    @Schema(description = "最后更新时间")
    private LocalDateTime updateTime;
}
