package com.chandler.learning.agent.learning.domain;

import cn.hutool.core.util.StrUtil;
import com.chandler.learning.agent.support.LearningConstants;
import lombok.Getter;

import java.util.Arrays;

/**
 * 单词熟练状态。
 * <p>
 * 该枚举用于约束单词本词条状态，避免 familiar/forgotten/vague 字符串散落在业务代码中。
 */
@Getter
public enum ReviewStatus {

    FAMILIAR(LearningConstants.Review.STATUS_FAMILIAR, "熟悉"),
    FORGOTTEN(LearningConstants.Review.STATUS_FORGOTTEN, "遗忘"),
    VAGUE(LearningConstants.Review.STATUS_VAGUE, "模糊");

    private final String code;
    private final String label;

    ReviewStatus(String code, String label) {
        this.code = code;
        this.label = label;
    }

    /**
     * 处理 {@code of} 相关业务。
     */
    public static ReviewStatus of(String code) {
        String normalized = StrUtil.blankToDefault(code, VAGUE.code).trim().toLowerCase();
        return Arrays.stream(values())
                .filter(status -> status.code.equals(normalized))
                .findFirst()
                .orElse(VAGUE);
    }

    /**
     * 处理 {@code infer} 相关业务。
     */
    public static ReviewStatus infer(Integer masteryScore, Integer wrongCount, Integer correctCount) {
        int mastery = masteryScore == null ? LearningConstants.ZERO : masteryScore;
        int wrong = wrongCount == null ? LearningConstants.ZERO : wrongCount;
        int correct = correctCount == null ? LearningConstants.ZERO : correctCount;
        if (mastery >= LearningConstants.Review.FAMILIAR_MASTERY_THRESHOLD) {
            return FAMILIAR;
        }
        if (wrong > correct) {
            return FORGOTTEN;
        }
        return VAGUE;
    }
}
