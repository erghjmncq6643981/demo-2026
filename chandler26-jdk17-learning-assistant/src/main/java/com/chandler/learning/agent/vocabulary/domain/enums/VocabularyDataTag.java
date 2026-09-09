package com.chandler.learning.agent.vocabulary.domain.enums;

import cn.hutool.core.util.StrUtil;
import com.chandler.learning.agent.exception.LearningAssistantException;
import com.chandler.learning.agent.common.exception.LearningErrorCode;
import lombok.Getter;

import java.util.Arrays;
import java.util.List;

/**
 * 词汇公共词本数据标签枚举。
 */
@Getter
public enum VocabularyDataTag {

    /** 小升初。 */
    PRIMARY_TO_MIDDLE("primary_to_middle", "小升初"),

    /** 高考。 */
    NCEE("ncee", "高考"),

    /** 自考英语。 */
    SELF_STUDY("self_study", "自考"),

    /** 托福考试。 */
    TOEFL("toefl", "托福"),

    /** 大学英语四级。 */
    CET4("cet4", "四级"),

    /** 大学英语六级。 */
    CET6("cet6", "六级"),

    /** 雅思考试。 */
    IELTS("ielts", "雅思");

    /** 标签编码。 */
    private final String code;

    /** 标签中文显示名称。 */
    private final String label;

    VocabularyDataTag(String code, String label) {
        this.code = code;
        this.label = label;
    }

    /**
     * 根据编码或中文名称匹配枚举（大小写不敏感，支持编码如 self_study 与中文如 自考）。
     *
     * @param text 编码或显示名称
     * @return 匹配的枚举；若为空或未匹配则返回 null
     */
    public static VocabularyDataTag from(String text) {
        if (StrUtil.isBlank(text)) {
            return null;
        }
        String trimmed = text.trim();
        String normalized = trimmed.toLowerCase();
        for (VocabularyDataTag tag : values()) {
            if (tag.code.equalsIgnoreCase(normalized) || tag.label.equalsIgnoreCase(trimmed)) {
                return tag;
            }
        }
        return null;
    }

    /**
     * 校验并获取枚举；不符合时抛出业务异常。
     *
     * @param text 编码或显示名称
     * @return 匹配的枚举
     */
    public static VocabularyDataTag require(String text) {
        VocabularyDataTag tag = from(text);
        if (tag != null) {
            return tag;
        }
        throw LearningAssistantException.badRequest(
                LearningErrorCode.VOCABULARY_IMPORT_INVALID,
                "数据标签仅支持自考、四级、六级或雅思");
    }

    /**
     * 获取所有标签列表。
     *
     * @return 所有枚举值列表
     */
    public static List<VocabularyDataTag> all() {
        return Arrays.asList(values());
    }
}
