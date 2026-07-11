package com.chandler.learning.agent.domain.enums;

import cn.hutool.core.util.StrUtil;
import com.chandler.learning.agent.support.LearningConstants;
import lombok.Getter;

import java.util.Arrays;
import java.util.List;

/**
 * 词汇关联关系类型。
 * <p>
 * 只有 synonym/antonym/word_family 进入“相关单词”展示，搭配继续留在搭配区域。
 */
@Getter
public enum VocabularyRelationType {

    SYNONYM(LearningConstants.VocabularyInsight.RELATION_TYPE_SYNONYM, "同义词", true, List.of("synonyms")),
    ANTONYM(LearningConstants.VocabularyInsight.RELATION_TYPE_ANTONYM, "反义词", true, List.of("antonyms")),
    WORD_FAMILY(LearningConstants.VocabularyInsight.RELATION_TYPE_WORD_FAMILY, "词族", true, List.of("word_family", "wordFamily")),
    TAG_OVERLAP(LearningConstants.VocabularyInsight.RELATION_TYPE_TAG_OVERLAP, "标签相似", false,
            List.of("synonyms", "antonyms", "word_family", "wordFamily", "collocations")),
    COLLOCATION(LearningConstants.VocabularyInsight.RELATION_TYPE_COLLOCATION, "搭配", false, List.of("collocations"));

    private final String code;
    private final String label;
    private final boolean visibleInRelatedWords;
    private final List<String> jsonFields;

    VocabularyRelationType(String code, String label, boolean visibleInRelatedWords, List<String> jsonFields) {
        this.code = code;
        this.label = label;
        this.visibleInRelatedWords = visibleInRelatedWords;
        this.jsonFields = jsonFields;
    }

    public static VocabularyRelationType of(String code) {
        String normalized = StrUtil.blankToDefault(code, TAG_OVERLAP.code).trim().toLowerCase();
        return Arrays.stream(values())
                .filter(type -> type.code.equals(normalized))
                .findFirst()
                .orElse(TAG_OVERLAP);
    }
}
