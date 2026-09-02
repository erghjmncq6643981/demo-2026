package com.chandler.learning.agent.ai.chat.application.codec;

import com.chandler.learning.agent.ai.chat.domain.enums.AiInvocationScene;
import com.chandler.learning.agent.ai.gateway.parser.AiStructuredResponseParseResult;
import com.chandler.learning.agent.exception.LearningAssistantException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiSceneResponseCodecRegistryTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AiSceneResponseCodecRegistry registry = new AiSceneResponseCodecRegistry(objectMapper);

    @Test
    void unwrapsSceneAndNormalizesAliasesOnce() throws Exception {
        var parsed = new AiStructuredResponseParseResult(objectMapper.readTree("""
                {"scene":{"scene_title":"机场出发","article":"Boarding starts.","translation":"开始登机。","words":[]}}
                """), "", "deepseek-json", "raw", List.of());

        AiSceneResponse response = registry.decode(AiInvocationScene.VOCABULARY_SCENE_UNIT, parsed);

        assertThat(response.root().path("title").asText()).isEqualTo("机场出发");
        assertThat(response.root().path("learning_text").asText()).isEqualTo("Boarding starts.");
        assertThat(response.root().path("vocabulary").isArray()).isTrue();
        assertThat(objectMapper.readTree(response.normalizedContent())).isEqualTo(response.root());
    }

    @Test
    void rejectsMissingSceneContractField() throws Exception {
        var parsed = new AiStructuredResponseParseResult(
                objectMapper.readTree("{\"title\":\"机场出发\"}"), "", "deepseek-json", "raw", List.of());

        assertThatThrownBy(() -> registry.decode(AiInvocationScene.VOCABULARY_SCENE_UNIT, parsed))
                .isInstanceOf(LearningAssistantException.class)
                .hasMessageContaining("learning_text");
    }

    @Test
    void decodesVocabularyCardSingleEvenWithoutMemoryTips() throws Exception {
        var parsed = new AiStructuredResponseParseResult(objectMapper.readTree("""
                {"word":"abandon","meaning":[{"part_of_speech":"v.","meaning":"放弃"}]}
                """), "", "kimi-json", "raw", List.of());

        AiSceneResponse response = registry.decode(AiInvocationScene.VOCABULARY_CARD_SINGLE, parsed);

        assertThat(response.root().path("term").asText()).isEqualTo("abandon");
        assertThat(response.root().path("definitions").isArray()).isTrue();
        assertThat(response.root().path("definitions").get(0).path("meaning").asText()).isEqualTo("放弃");
    }

    @Test
    void normalizesMemoryTipsAliasForVocabularyCard() throws Exception {
        var parsed = new AiStructuredResponseParseResult(objectMapper.readTree("""
                {"term":"abandon","definitions":[{"meaning":"放弃"}],"memoryTips":"把 abandon 想成放开控制"}
                """), "", "kimi-json", "raw", List.of());

        AiSceneResponse response = registry.decode(AiInvocationScene.VOCABULARY_CARD_SINGLE, parsed);

        assertThat(response.root().path("term").asText()).isEqualTo("abandon");
        assertThat(response.root().path("memory_tips").asText()).isEqualTo("把 abandon 想成放开控制");
    }
}
