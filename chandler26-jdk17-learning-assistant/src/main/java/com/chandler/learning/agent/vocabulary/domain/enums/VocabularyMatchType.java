package com.chandler.learning.agent.vocabulary.domain.enums;

import cn.hutool.core.util.StrUtil;
import com.chandler.learning.agent.vocabulary.domain.constant.VocabularyInsightConstants;
import lombok.Getter;

import java.util.Arrays;

/**
 * 词汇匹配来源。
 */
@Getter
public enum VocabularyMatchType {

    EXACT(VocabularyInsightConstants.MATCH_TYPE_EXACT, "精确匹配"),
    FUZZY(VocabularyInsightConstants.MATCH_TYPE_FUZZY, "模糊匹配"),
    PARSED_TEXT(VocabularyInsightConstants.MATCH_TYPE_PARSED_TEXT, "AI 文本解析"),
    PARSED_OBJECT(VocabularyInsightConstants.MATCH_TYPE_PARSED_OBJECT, "AI 对象解析"),
    CACHED_EXACT(VocabularyInsightConstants.MATCH_TYPE_CACHED_EXACT, "缓存精确匹配");

    private final String code;
    private final String label;

    VocabularyMatchType(String code, String label) {
        this.code = code;
        this.label = label;
    }

    /**
     * 处理 {@code of} 相关业务。
     */
    public static VocabularyMatchType of(String code) {
        String normalized = StrUtil.blankToDefault(code, PARSED_TEXT.code).trim().toLowerCase();
        return Arrays.stream(values())
                .filter(type -> type.code.equals(normalized))
                .findFirst()
                .orElse(PARSED_TEXT);
    }
}
