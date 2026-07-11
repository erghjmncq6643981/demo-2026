package com.chandler.learning.agent.domain.enums;

import com.chandler.learning.agent.support.LearningConstants;
import lombok.Getter;

/**
 * AI 词汇卡片推导出的学习难度。
 */
@Getter
public enum VocabularyDifficulty {

    EASY(LearningConstants.VocabularyInsight.DIFFICULTY_EASY, "简单"),
    MEDIUM(LearningConstants.VocabularyInsight.DIFFICULTY_MEDIUM, "中等"),
    HARD(LearningConstants.VocabularyInsight.DIFFICULTY_HARD, "困难");

    private final String code;
    private final String label;

    VocabularyDifficulty(String code, String label) {
        this.code = code;
        this.label = label;
    }
}
