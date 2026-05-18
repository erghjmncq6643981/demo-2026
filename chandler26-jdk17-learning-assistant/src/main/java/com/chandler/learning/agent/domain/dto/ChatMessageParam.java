package com.chandler.learning.agent.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 模型对话消息参数。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageParam {

    private String role;

    private String content;
}
