package com.chandler.learning.agent.support;

import com.chandler.learning.agent.domain.enums.AiInvocationScene;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiStructuredResponseParserRegistryTest {

    private final AiStructuredResponseParserRegistry registry = new AiStructuredResponseParserRegistry(new ObjectMapper());

    @Test
    void deepSeekReadsRawJsonBeforeApplyingAnyRepair() {
        String content = "{\"meaning\":\"解释“牺牲”的意思\",\"items\":[1]}";

        AiStructuredResponseParseResult result = registry.parse(AiInvocationScene.VOCABULARY_SCENE_UNIT,
                "deepseek", "deepseek-chat", content);

        assertThat(result.parserName()).isEqualTo("deepseek-json");
        assertThat(result.parseStage()).isEqualTo("raw");
        assertThat(result.repairs()).isEmpty();
        assertThat(result.root().path("meaning").asText()).isEqualTo("解释“牺牲”的意思");
    }

    @Test
    void kimiUsesConservativeRepairOnlyAfterStrictParseFails() {
        String content = "{\"term\": abandon，\"meaning\":\"解释“牺牲”的意思\",\"items\":[1,],";

        AiStructuredResponseParseResult result = registry.parse(AiInvocationScene.VOCABULARY_SCENE_UNIT,
                "moonshot", "moonshot-v1-8k", content);

        assertThat(result.parserName()).isEqualTo("kimi-json");
        assertThat(result.parseStage()).isEqualTo("repaired");
        assertThat(result.repairs()).contains("normalized_structural_punctuation", "quoted_known_bare_value",
                "removed_trailing_comma", "completed_trailing_brackets");
        assertThat(result.root().path("term").asText()).isEqualTo("abandon");
        assertThat(result.root().path("meaning").asText()).isEqualTo("解释“牺牲”的意思");
    }

    @Test
    void resolvesContextWindowsInTokensNotBytes() {
        AiModelCapabilityResolver resolver = new AiModelCapabilityResolver();

        assertThat(resolver.contextWindowTokens("deepseek", "deepseek-chat")).isEqualTo(8_192);
        assertThat(resolver.safeContextWindowTokens("moonshot", "moonshot-v1-8k")).isEqualTo(7_372);
    }
}
