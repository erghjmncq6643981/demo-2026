package com.chandler.learning.agent.ai.agent.domain;

import cn.hutool.core.util.StrUtil;
import com.chandler.learning.agent.support.LearningConstants;
import lombok.Getter;

import java.util.Arrays;

/**
 * Agent 能力类型。
 */
@Getter
public enum AiAgentType {

    CHAT(LearningConstants.Agent.TYPE_CHAT, "对话"),
    ANALYSIS(LearningConstants.Agent.TYPE_ANALYSIS, "分析"),
    ASSISTANT(LearningConstants.Agent.TYPE_ASSISTANT, "助手");

    private final String code;
    private final String label;

    AiAgentType(String code, String label) {
        this.code = code;
        this.label = label;
    }

    /**
     * 处理 {@code of} 相关业务。
     */
    public static AiAgentType of(String code) {
        String normalized = StrUtil.blankToDefault(code, CHAT.code).trim().toLowerCase();
        return Arrays.stream(values())
                .filter(type -> type.code.equals(normalized))
                .findFirst()
                .orElse(CHAT);
    }
}
