package com.chandler.learning.agent.ai.chat.api;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对话会话响应。
 */
@Data
public class ChatSessionResponse {

    /** 会话主键。 */
    private Long id;

    /** 会话所属用户 ID。 */
    private Long userId;

    /** Agent 编码。 */
    private String agentCode;

    /** 业务类型，例如 learning。 */
    private String businessType;

    /** 业务对象 ID。 */
    private String businessId;

    /** 可复用的学习场景编码。 */
    private String sceneCode;

    /** 会话展示标题。 */
    private String title;

    /** 当前会话的有效消息数量。 */
    private Integer messageCount;

    /** 创建时间。 */
    private LocalDateTime createTime;

    /** 最近更新时间。 */
    private LocalDateTime updateTime;
}
