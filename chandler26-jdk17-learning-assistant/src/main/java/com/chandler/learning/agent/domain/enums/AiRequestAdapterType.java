package com.chandler.learning.agent.domain.enums;

import lombok.Getter;

/**
 * 模型请求预处理适配器类型。
 */
@Getter
public enum AiRequestAdapterType {

    DEEPSEEK_CHAT("deepseek_chat"),
    KIMI_CHAT("kimi_chat");

    private final String code;

    AiRequestAdapterType(String code) {
        this.code = code;
    }
}
