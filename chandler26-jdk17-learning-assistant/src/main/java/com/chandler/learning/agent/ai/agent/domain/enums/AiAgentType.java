package com.chandler.learning.agent.ai.agent.domain.enums;

import cn.hutool.core.util.StrUtil;
import com.chandler.learning.agent.ai.agent.domain.constant.AiAgentConstants;
import lombok.Getter;

import java.util.Arrays;

/**
 * Agent 能力类型。
 */
@Getter
public enum AiAgentType {

    CHAT(AiAgentConstants.TYPE_CHAT, "对话"),
    ANALYSIS(AiAgentConstants.TYPE_ANALYSIS, "分析"),
    ASSISTANT(AiAgentConstants.TYPE_ASSISTANT, "助手");

    private final String code;
    private final String label;

    AiAgentType(String code, String label) {
        this.code = code;
        this.label = label;
    }

    /** 按编码解析对应的业务枚举。 */
    public static AiAgentType of(String code) {
        String normalized = StrUtil.blankToDefault(code, CHAT.code).trim().toLowerCase();
        return Arrays.stream(values())
                .filter(type -> type.code.equals(normalized))
                .findFirst()
                .orElse(CHAT);
    }
}
