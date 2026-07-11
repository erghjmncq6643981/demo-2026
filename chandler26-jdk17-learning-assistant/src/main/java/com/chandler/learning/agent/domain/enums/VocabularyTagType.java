package com.chandler.learning.agent.domain.enums;

import cn.hutool.core.util.StrUtil;
import com.chandler.learning.agent.support.LearningConstants;
import lombok.Getter;

import java.util.Arrays;

/**
 * 词汇标签类型。
 */
@Getter
public enum VocabularyTagType {

    PART_OF_SPEECH(LearningConstants.VocabularyInsight.TAG_TYPE_PART_OF_SPEECH, "词性"),
    MEANING_TOPIC(LearningConstants.VocabularyInsight.TAG_TYPE_MEANING_TOPIC, "含义主题"),
    DIFFICULTY(LearningConstants.VocabularyInsight.TAG_TYPE_DIFFICULTY, "难度"),
    COLLOCATION(LearningConstants.VocabularyInsight.RELATION_TYPE_COLLOCATION, "搭配"),
    WORD_FAMILY(LearningConstants.VocabularyInsight.RELATION_TYPE_WORD_FAMILY, "词族");

    private final String code;
    private final String label;

    VocabularyTagType(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public static VocabularyTagType of(String code) {
        String normalized = StrUtil.blankToDefault(code, MEANING_TOPIC.code).trim().toLowerCase();
        return Arrays.stream(values())
                .filter(type -> type.code.equals(normalized))
                .findFirst()
                .orElse(MEANING_TOPIC);
    }
}
