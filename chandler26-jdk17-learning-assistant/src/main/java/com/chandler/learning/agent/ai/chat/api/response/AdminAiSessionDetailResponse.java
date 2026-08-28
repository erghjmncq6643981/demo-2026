package com.chandler.learning.agent.ai.chat.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Data;

import java.util.List;

/** AI 会话、消息和模型调用的完整诊断信息。 */
@Data
public class AdminAiSessionDetailResponse {

    @Schema(description = "会话信息")
    private AdminAiSessionResponse session;
    @Schema(description = "会话消息列表")
    private List<ChatMessageResponse> messages;
    @Schema(description = "模型调用记录列表")
    private List<AiModelCallRecordResponse> calls;
}
