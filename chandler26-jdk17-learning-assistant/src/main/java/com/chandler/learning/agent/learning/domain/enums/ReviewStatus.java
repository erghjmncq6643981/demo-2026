package com.chandler.learning.agent.learning.domain.enums;

import cn.hutool.core.util.StrUtil;
import com.chandler.learning.agent.common.constant.CommonConstants;
import com.chandler.learning.agent.vocabulary.domain.constant.ReviewConstants;
import lombok.Getter;

import java.util.Arrays;

/**
 * 单词熟练状态。
 * <p>
 * 该枚举用于约束单词本词条状态，避免 familiar/forgotten/vague 字符串散落在业务代码中。
 */
@Getter
public enum ReviewStatus {

    FAMILIAR(ReviewConstants.STATUS_FAMILIAR, "熟悉"),
    FORGOTTEN(ReviewConstants.STATUS_FORGOTTEN, "遗忘"),
    VAGUE(ReviewConstants.STATUS_VAGUE, "模糊");

    private final String code;
    private final String label;

    ReviewStatus(String code, String label) {
        this.code = code;
        this.label = label;
    }

    /** 按编码解析对应的业务枚举。 */
    public static ReviewStatus of(String code) {
        String normalized = StrUtil.blankToDefault(code, VAGUE.code).trim().toLowerCase();
        return Arrays.stream(values())
                .filter(status -> status.code.equals(normalized))
                .findFirst()
                .orElse(VAGUE);
    }

    /** 根据历史数据推断对应业务枚举。 */
    public static ReviewStatus infer(Integer masteryScore, Integer wrongCount, Integer correctCount) {
        int mastery = masteryScore == null ? CommonConstants.ZERO : masteryScore;
        int wrong = wrongCount == null ? CommonConstants.ZERO : wrongCount;
        int correct = correctCount == null ? CommonConstants.ZERO : correctCount;
        if (mastery >= ReviewConstants.FAMILIAR_MASTERY_THRESHOLD) {
            return FAMILIAR;
        }
        if (wrong > correct) {
            return FORGOTTEN;
        }
        return VAGUE;
    }
}
