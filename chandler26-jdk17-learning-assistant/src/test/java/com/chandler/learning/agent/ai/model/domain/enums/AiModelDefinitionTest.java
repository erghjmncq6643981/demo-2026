package com.chandler.learning.agent.ai.model.domain.enums;

import com.chandler.learning.agent.ai.gateway.protocol.AiRequestAdapterType;
import com.chandler.learning.agent.ai.gateway.protocol.AiResponseParserType;
import com.chandler.learning.agent.exception.LearningAssistantException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiModelDefinitionTest {

    @Test
    void resolvesQwenAndGlmProvidersWithAliases() {
        assertThat(AiProvider.of("qwen")).isEqualTo(AiProvider.QWEN);
        assertThat(AiProvider.of("dashscope")).isEqualTo(AiProvider.QWEN);
        assertThat(AiProvider.of("aliyun")).isEqualTo(AiProvider.QWEN);
        assertThat(AiProvider.of("QWEN")).isEqualTo(AiProvider.QWEN);

        assertThat(AiProvider.of("glm")).isEqualTo(AiProvider.GLM);
        assertThat(AiProvider.of("zhipu")).isEqualTo(AiProvider.GLM);
        assertThat(AiProvider.of("bigmodel")).isEqualTo(AiProvider.GLM);
        assertThat(AiProvider.of("GLM")).isEqualTo(AiProvider.GLM);
    }

    @Test
    void resolvesQwen38FlashModel() {
        AiModelDefinition model = AiModelDefinition.resolve("qwen", "qwen3.8-flash");
        assertThat(model).isEqualTo(AiModelDefinition.QWEN_3_8_FLASH);
        assertThat(model.getProvider()).isEqualTo(AiProvider.QWEN);
        assertThat(model.getRequestAdapter()).isEqualTo(AiRequestAdapterType.QWEN_CHAT);
        assertThat(model.getResponseParser()).isEqualTo(AiResponseParserType.STRICT_JSON);
        assertThat(model.getContextWindowTokens()).isEqualTo(1_048_576);
        assertThat(model.getMaxOutputTokens()).isEqualTo(8_192);
    }

    @Test
    void resolvesGlm53FlashModelCaseInsensitive() {
        AiModelDefinition upper = AiModelDefinition.resolve("glm", "GLM-5.3-Flash");
        assertThat(upper).isEqualTo(AiModelDefinition.GLM_5_3_FLASH);

        AiModelDefinition lower = AiModelDefinition.resolve("glm", "glm-5.3-flash");
        assertThat(lower).isEqualTo(AiModelDefinition.GLM_5_3_FLASH);

        assertThat(upper.getProvider()).isEqualTo(AiProvider.GLM);
        assertThat(upper.getRequestAdapter()).isEqualTo(AiRequestAdapterType.GLM_CHAT);
        assertThat(upper.getResponseParser()).isEqualTo(AiResponseParserType.STRICT_JSON);
        assertThat(upper.getContextWindowTokens()).isEqualTo(131_072);
        assertThat(upper.getMaxOutputTokens()).isEqualTo(8_192);
    }

    @Test
    void supportsChecksValidityWithProviderAliases() {
        assertThat(AiModelDefinition.supports("qwen", "qwen3.8-flash")).isTrue();
        assertThat(AiModelDefinition.supports("dashscope", "qwen3.8-flash")).isTrue();
        assertThat(AiModelDefinition.supports("glm", "GLM-5.3-Flash")).isTrue();
        assertThat(AiModelDefinition.supports("glm", "glm-5.3-flash")).isTrue();
        assertThat(AiModelDefinition.supports("zhipu", "glm-5.3-flash")).isTrue();

        assertThat(AiModelDefinition.supports("unknown", "model")).isFalse();
        assertThat(AiModelDefinition.supports("qwen", "unknown-model")).isFalse();
    }

    @Test
    void throwsOnUnsupportedModel() {
        assertThatThrownBy(() -> AiModelDefinition.resolve("qwen", "non-existent"))
                .isInstanceOf(LearningAssistantException.class);
    }
}
