package com.chandler.learning.agent.ai.chat.api;

import lombok.Data;

import java.util.List;

/** AI 会话、消息和模型调用的完整诊断信息。 */
@Data
public class AdminAiSessionDetailResponse {

    private AdminAiSessionResponse session;
    private List<ChatMessageResponse> messages;
    private List<AiModelCallRecordResponse> calls;
}
