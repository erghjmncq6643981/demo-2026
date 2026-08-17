package com.chandler.learning.agent.service.vocabulary;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MarkdownVocabularyParserTest {

    private final MarkdownVocabularyParser parser = new MarkdownVocabularyParser();

    @Test
    void parsesReferenceSelfStudyVocabularyAndFindsKnownSplitWords() throws Exception {
        String markdown = Files.readString(
                Path.of("docs", "自学考试(二)全部词汇5087_正序版.md"),
                StandardCharsets.UTF_8);

        List<MarkdownVocabularyParser.ParsedVocabulary> words = parser.parse(markdown);

        assertThat(words).hasSize(5_087);
        assertThat(words.stream().filter(MarkdownVocabularyParser.ParsedVocabulary::suspicious)).hasSize(7);
        assertThat(words.get(0).sourceOrder()).isEqualTo(1);
        assertThat(words.get(words.size() - 1).sourceOrder()).isEqualTo(5_087);
        assertThat(find(words, 112))
                .extracting(MarkdownVocabularyParser.ParsedVocabulary::originalTerm,
                        MarkdownVocabularyParser.ParsedVocabulary::suggestedTerm,
                        MarkdownVocabularyParser.ParsedVocabulary::suspicious)
                .containsExactly("air-conditionin g", "air-conditioning", true);
        assertThat(find(words, 5_018))
                .extracting(MarkdownVocabularyParser.ParsedVocabulary::originalTerm,
                        MarkdownVocabularyParser.ParsedVocabulary::suggestedTerm,
                        MarkdownVocabularyParser.ParsedVocabulary::suspicious)
                .containsExactly("butterflies in the s tomach", "butterflies in the stomach", true);
    }

    @Test
    void usesNamedHeadersAndIgnoresNoColumn() {
        String markdown = """
                | No. | 释义 | Word | 序号 | 音标 |
                | --- | --- | --- | --- | --- |
                | 900 | 城市 | city | 7 | /ˈsɪti/ |
                """;

        List<MarkdownVocabularyParser.ParsedVocabulary> words = parser.parse(markdown);

        assertThat(words).singleElement().satisfies(word -> {
            assertThat(word.sourceOrder()).isEqualTo(7);
            assertThat(word.originalTerm()).isEqualTo("city");
            assertThat(word.phonetic()).isEqualTo("/ˈsɪti/");
            assertThat(word.definition()).isEqualTo("城市");
        });
    }

    private MarkdownVocabularyParser.ParsedVocabulary find(
            List<MarkdownVocabularyParser.ParsedVocabulary> words, int sourceOrder) {
        return words.stream()
                .filter(word -> word.sourceOrder() == sourceOrder)
                .findFirst()
                .orElseThrow();
    }
}
