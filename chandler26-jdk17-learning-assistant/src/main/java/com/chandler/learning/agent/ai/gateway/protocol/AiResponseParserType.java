package com.chandler.learning.agent.ai.gateway.protocol;

import lombok.Getter;

/**
 * 模型正文结构化响应解析器类型。
 */
@Getter
public enum AiResponseParserType {

    DEEPSEEK_JSON("deepseek_json"),
    KIMI_JSON("kimi_json"),
    STRICT_JSON("strict_json");

    private final String code;

    AiResponseParserType(String code) {
        this.code = code;
    }
}
