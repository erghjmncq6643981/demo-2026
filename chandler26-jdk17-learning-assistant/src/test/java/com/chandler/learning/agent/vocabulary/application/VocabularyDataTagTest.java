package com.chandler.learning.agent.vocabulary.application;

import com.chandler.learning.agent.common.exception.LearningErrorCode;
import com.chandler.learning.agent.exception.LearningAssistantException;
import com.chandler.learning.agent.vocabulary.domain.enums.VocabularyDataTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("词汇数据标签枚举测试")
class VocabularyDataTagTest {

    @ParameterizedTest
    @CsvSource({
            "self_study, SELF_STUDY",
            "SELF_STUDY, SELF_STUDY",
            "自考, SELF_STUDY",
            "cet4, CET4",
            "CET4, CET4",
            "四级, CET4",
            "cet6, CET6",
            "CET6, CET6",
            "六级, CET6",
            "ielts, IELTS",
            "IELTS, IELTS",
            "雅思, IELTS"
    })
    @DisplayName("支持通过编码或中文名称精确/不分大小写匹配")
    void testFromValid(String input, VocabularyDataTag expected) {
        assertThat(VocabularyDataTag.from(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "   ", "toefl", "考研", "unknown"})
    @DisplayName("空白或不支持的文本返回 null")
    void testFromInvalid(String input) {
        assertThat(VocabularyDataTag.from(input)).isNull();
    }

    @Test
    @DisplayName("null 输入返回 null")
    void testFromNull() {
        assertThat(VocabularyDataTag.from(null)).isNull();
    }

    @Test
    @DisplayName("require 正常匹配返回枚举并获取其规范编码与标签")
    void testRequireValid() {
        VocabularyDataTag tag = VocabularyDataTag.require("四级");
        assertThat(tag).isEqualTo(VocabularyDataTag.CET4);
        assertThat(tag.getCode()).isEqualTo("cet4");
        assertThat(tag.getLabel()).isEqualTo("四级");

        assertThat(VocabularyDataTag.require("self_study").getCode()).isEqualTo("self_study");
        assertThat(VocabularyDataTag.require("六级").getCode()).isEqualTo("cet6");
        assertThat(VocabularyDataTag.require("雅思").getCode()).isEqualTo("ielts");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "toefl", "考研"})
    @DisplayName("require 不支持的文本抛出业务异常")
    void testRequireInvalid(String input) {
        assertThatThrownBy(() -> VocabularyDataTag.require(input))
                .isInstanceOf(LearningAssistantException.class)
                .hasMessage("数据标签仅支持自考、四级、六级或雅思")
                .satisfies(ex -> assertThat(((LearningAssistantException) ex).getErrorCode())
                        .isEqualTo(LearningErrorCode.VOCABULARY_IMPORT_INVALID.getCode()));
    }

    @Test
    @DisplayName("require null 输入抛出业务异常")
    void testRequireNull() {
        assertThatThrownBy(() -> VocabularyDataTag.require(null))
                .isInstanceOf(LearningAssistantException.class)
                .hasMessage("数据标签仅支持自考、四级、六级或雅思");
    }

    @Test
    @DisplayName("all 包含全部4个数据标签")
    void testAll() {
        List<VocabularyDataTag> all = VocabularyDataTag.all();
        assertThat(all).containsExactly(
                VocabularyDataTag.SELF_STUDY,
                VocabularyDataTag.CET4,
                VocabularyDataTag.CET6,
                VocabularyDataTag.IELTS
        );
    }
}
