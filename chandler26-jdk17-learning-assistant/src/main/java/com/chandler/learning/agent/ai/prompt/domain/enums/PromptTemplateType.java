package com.chandler.learning.agent.ai.prompt.domain.enums;

import cn.hutool.core.util.StrUtil;
import com.chandler.learning.agent.ai.prompt.domain.constant.AiPromptTemplateConstants;
import lombok.Getter;

import java.util.Arrays;

/**
 * 提示词模板消息类型。
 */
@Getter
public enum PromptTemplateType {

    SYSTEM(AiPromptTemplateConstants.TYPE_SYSTEM, "系统提示词"),
    USER(AiPromptTemplateConstants.TYPE_USER, "用户提示词"),
    ANALYSIS(AiPromptTemplateConstants.TYPE_ANALYSIS, "分析提示词");

    private final String code;
    private final String label;

    PromptTemplateType(String code, String label) {
        this.code = code;
        this.label = label;
    }

    /**
     * 处理 {@code of} 相关业务。
     */
    public static PromptTemplateType of(String code) {
        String normalized = StrUtil.blankToDefault(code, USER.code).trim().toLowerCase();
        return Arrays.stream(values())
                .filter(type -> type.code.equals(normalized))
                .findFirst()
                .orElse(USER);
    }
}
