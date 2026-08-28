package com.chandler.learning.agent.vocabulary.domain.enums;

import com.chandler.learning.agent.vocabulary.domain.constant.VocabularyInsightConstants;
import lombok.Getter;

/**
 * AI 词汇卡片推导出的学习难度。
 */
@Getter
public enum VocabularyDifficulty {

    EASY(VocabularyInsightConstants.DIFFICULTY_EASY, "简单"),
    MEDIUM(VocabularyInsightConstants.DIFFICULTY_MEDIUM, "中等"),
    HARD(VocabularyInsightConstants.DIFFICULTY_HARD, "困难");

    private final String code;
    private final String label;

    VocabularyDifficulty(String code, String label) {
        this.code = code;
        this.label = label;
    }
}
