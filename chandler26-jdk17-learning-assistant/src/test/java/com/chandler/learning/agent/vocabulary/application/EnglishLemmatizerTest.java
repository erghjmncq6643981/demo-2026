package com.chandler.learning.agent.vocabulary.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("英语词形还原器单测")
class EnglishLemmatizerTest {

    private EnglishLemmatizer lemmatizer;

    @BeforeEach
    void setUp() {
        lemmatizer = new EnglishLemmatizer();
    }

    @Test
    @DisplayName("常见不规则动词与不规则复数应精准推导原型")
    void shouldResolveIrregularFormsAccurately() {
        assertThat(lemmatizer.candidateLemmas("went")).contains("go");
        assertThat(lemmatizer.candidateLemmas("ran")).contains("run");
        assertThat(lemmatizer.candidateLemmas("children")).contains("child");
        assertThat(lemmatizer.candidateLemmas("people")).contains("person");
        assertThat(lemmatizer.candidateLemmas("better")).contains("good");
        assertThat(lemmatizer.candidateLemmas("leaves")).contains("leaf");
        assertThat(lemmatizer.candidateLemmas("knives")).contains("knife");
    }

    @Test
    @DisplayName("规则分词、时态与复数应推导出合理的原型候选")
    void shouldResolveRegularInflectionCandidates() {
        // -ing
        assertThat(lemmatizer.candidateLemmas("running")).contains("run");
        assertThat(lemmatizer.candidateLemmas("making")).contains("make");
        assertThat(lemmatizer.candidateLemmas("playing")).contains("play");
        assertThat(lemmatizer.candidateLemmas("tying")).contains("tie");

        // -ed
        assertThat(lemmatizer.candidateLemmas("studied")).contains("study");
        assertThat(lemmatizer.candidateLemmas("stopped")).contains("stop");
        assertThat(lemmatizer.candidateLemmas("loved")).contains("love");

        // -s / -es
        assertThat(lemmatizer.candidateLemmas("apples")).contains("apple");
        assertThat(lemmatizer.candidateLemmas("watches")).contains("watch");
        assertThat(lemmatizer.candidateLemmas("stories")).contains("story");

        // -er / -est
        assertThat(lemmatizer.candidateLemmas("happier")).contains("happy");
        assertThat(lemmatizer.candidateLemmas("happiest")).contains("happy");
        assertThat(lemmatizer.candidateLemmas("bigger")).contains("big");
    }

    @Test
    @DisplayName("非屈折变化的独立词汇（如 sibling, modest, forest, darling）不应被误推导变形")
    void shouldNotFalselyStemIndependentNouns() {
        assertThat(lemmatizer.candidateLemmas("sibling")).isEmpty();
        assertThat(lemmatizer.candidateLemmas("darling")).isEmpty();
        assertThat(lemmatizer.candidateLemmas("duckling")).isEmpty();
        assertThat(lemmatizer.candidateLemmas("morning")).isEmpty();
        assertThat(lemmatizer.candidateLemmas("evening")).isEmpty();
        assertThat(lemmatizer.candidateLemmas("ceiling")).isEmpty();
        assertThat(lemmatizer.candidateLemmas("modest")).isEmpty();
        assertThat(lemmatizer.candidateLemmas("forest")).isEmpty();
        assertThat(lemmatizer.candidateLemmas("honest")).isEmpty();
        assertThat(lemmatizer.candidateLemmas("interest")).isEmpty();
        assertThat(lemmatizer.candidateLemmas("guest")).isEmpty();
        assertThat(lemmatizer.isNonInflectional("modest")).isTrue();
        assertThat(lemmatizer.isNonInflectional("sibling")).isTrue();
        assertThat(lemmatizer.isNonInflectional("nicest")).isFalse();
    }

    @Test
    @DisplayName("空值或单字符应安全处理")
    void shouldHandleEmptyOrSingleCharSafely() {
        assertThat(lemmatizer.candidateLemmas(null)).isEmpty();
        assertThat(lemmatizer.candidateLemmas("")).isEmpty();
        assertThat(lemmatizer.candidateLemmas("a")).isEmpty();
    }
}
