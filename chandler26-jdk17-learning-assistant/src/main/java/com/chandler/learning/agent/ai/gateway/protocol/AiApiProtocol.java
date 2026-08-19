package com.chandler.learning.agent.ai.gateway.protocol;

import lombok.Getter;

/**
 * AI 供应商 API 协议。
 */
@Getter
public enum AiApiProtocol {

    OPENAI_CHAT_COMPLETIONS("openai_chat_completions", "OpenAI Chat Completions");

    private final String code;
    private final String title;

    AiApiProtocol(String code, String title) {
        this.code = code;
        this.title = title;
    }
}
