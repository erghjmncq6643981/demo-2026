package com.chandler.learning.agent.ai.gateway.parser;

import com.chandler.learning.agent.ai.gateway.protocol.AiModelCapabilityResolver;
import com.chandler.learning.agent.ai.chat.domain.AiInvocationScene;
import com.chandler.learning.agent.ai.gateway.protocol.AiResponseParserType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiStructuredResponseParserRegistryTest {

    private final AiStructuredResponseParserRegistry registry = new AiStructuredResponseParserRegistry(new ObjectMapper());

    @Test
    void deepSeekReadsRawJsonBeforeApplyingAnyRepair() {
        String content = "{\"meaning\":\"解释“牺牲”的意思\",\"items\":[1]}";

        AiStructuredResponseParseResult result = registry.parse(AiInvocationScene.VOCABULARY_SCENE_UNIT,
                AiResponseParserType.DEEPSEEK_JSON, content);

        assertThat(result.parserName()).isEqualTo("deepseek-json");
        assertThat(result.parseStage()).isEqualTo("raw");
        assertThat(result.repairs()).isEmpty();
        assertThat(result.root().path("meaning").asText()).isEqualTo("解释“牺牲”的意思");
    }

    @Test
    void kimiUsesConservativeRepairOnlyAfterStrictParseFails() {
        String content = "{\"term\": abandon，\"meaning\":\"解释“牺牲”的意思\",\"items\":[1,],";

        AiStructuredResponseParseResult result = registry.parse(AiInvocationScene.VOCABULARY_SCENE_UNIT,
                AiResponseParserType.KIMI_JSON, content);

        assertThat(result.parserName()).isEqualTo("kimi-json");
        assertThat(result.parseStage()).isEqualTo("repaired");
        assertThat(result.repairs()).contains("normalized_structural_punctuation", "quoted_known_bare_value",
                "removed_trailing_comma", "completed_trailing_brackets");
        assertThat(result.root().path("term").asText()).isEqualTo("abandon");
        assertThat(result.root().path("meaning").asText()).isEqualTo("解释“牺牲”的意思");
    }

    @Test
    void kimiRepairsBareKeysAndSmartQuotes() {
        String content = "{\n  “term”: abandon,\n  meaning: \"解释“牺牲”的意思\",\n  “context_meaning”: \"放弃\",\n  “correct_answer”: 1,\n}";

        AiStructuredResponseParseResult result = registry.parse(AiInvocationScene.VOCABULARY_SCENE_UNIT,
                AiResponseParserType.KIMI_JSON, content);

        assertThat(result.parserName()).isEqualTo("kimi-json");
        assertThat(result.parseStage()).isEqualTo("repaired");
        assertThat(result.repairs()).contains("normalized_structural_punctuation", "quoted_known_bare_value",
                "quoted_known_bare_key", "removed_trailing_comma");
        assertThat(result.root().path("term").asText()).isEqualTo("abandon");
        assertThat(result.root().path("meaning").asText()).isEqualTo("解释“牺牲”的意思");
        assertThat(result.root().path("context_meaning").asText()).isEqualTo("放弃");
    }

    @Test
    void kimiRepairsUnbracketedArrayAndMissingCommas() {
        String content = "{\n" +
                "  \"title\": \"机场出行\",\n" +
                "  \"learning_text\": \"This is an airport story.\",\n" +
                "  \"translation\": \"这是机场故事。\",\n" +
                "  \"vocabulary\":\n" +
                "  {\n" +
                "    \"term\": \"abandon\",\n" +
                "    \"meaning\": \"放弃\"\n" +
                "  }\n" +
                "  {\n" +
                "    \"term\": \"ability\",\n" +
                "    \"meaning\": \"能力\"\n" +
                "  }\n" +
                "}";

        AiStructuredResponseParseResult result = registry.parse(AiInvocationScene.VOCABULARY_SCENE_UNIT,
                AiResponseParserType.KIMI_JSON, content);

        assertThat(result.parserName()).isEqualTo("kimi-json");
        assertThat(result.parseStage()).isEqualTo("repaired");
        assertThat(result.repairs()).contains("inserted_missing_commas", "wrapped_unbracketed_array");
        assertThat(result.root().path("title").asText()).isEqualTo("机场出行");
        assertThat(result.root().path("vocabulary")).hasSize(2);
        assertThat(result.root().path("vocabulary").get(0).path("term").asText()).isEqualTo("abandon");
        assertThat(result.root().path("vocabulary").get(1).path("term").asText()).isEqualTo("ability");
    }

    @Test
    void resolvesContextWindowsInTokensNotBytes() {
        AiModelCapabilityResolver resolver = new AiModelCapabilityResolver();

        assertThat(resolver.contextWindowTokens("deepseek", "deepseek-v4-pro")).isEqualTo(1_048_576);
        assertThat(resolver.contextWindowTokens("kimi", "kimi-k2.6")).isEqualTo(262_144);
        assertThat(resolver.safeContextWindowTokens("kimi", "kimi-k3")).isEqualTo(943_718);
    }
}
