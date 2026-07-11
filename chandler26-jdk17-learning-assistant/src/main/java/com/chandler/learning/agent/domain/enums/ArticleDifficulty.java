package com.chandler.learning.agent.domain.enums;

import cn.hutool.core.util.StrUtil;
import com.chandler.learning.agent.exception.LearningAssistantException;
import com.chandler.learning.agent.support.LearningConstants;
import lombok.Getter;

import java.util.Arrays;

/**
 * 文章学习难度枚举。
 * <p>
 * 用固定难度控制文章用词、句式和语法讲解深度，避免前端自由传值导致提示词不稳定。
 */
@Getter
public enum ArticleDifficulty {

    EASY("easy", "基础", "适合初中到高中入门水平，句子短，语法解释直接"),
    MEDIUM("medium", "适中", "适合高中到四级水平，包含常见从句和自然衔接"),
    HARD("hard", "挑战", "适合四级以上水平，句式更丰富，知识点解释更深入");

    private final String code;
    private final String label;
    private final String prompt;

    ArticleDifficulty(String code, String label, String prompt) {
        this.code = code;
        this.label = label;
        this.prompt = prompt;
    }

    public static ArticleDifficulty of(String code) {
        String normalized = StrUtil.blankToDefault(code, MEDIUM.code).trim().toLowerCase();
        return Arrays.stream(values())
                .filter(item -> item.code.equals(normalized))
                .findFirst()
                .orElseThrow(() -> LearningAssistantException.badRequest(
                        LearningConstants.ErrorCode.ARTICLE_WORDS_INVALID,
                        "文章难度不支持: " + code));
    }
}
