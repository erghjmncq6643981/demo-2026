package com.chandler.learning.agent.domain.enums;

import com.chandler.learning.agent.exception.LearningAssistantException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiInvocationSceneTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldResolveStableCodeAndDefaultToGeneralChat() {
        assertThat(AiInvocationScene.of("vocabulary_card_batch"))
                .isEqualTo(AiInvocationScene.VOCABULARY_CARD_BATCH);
        assertThat(AiInvocationScene.of(null)).isEqualTo(AiInvocationScene.GENERAL_CHAT);
    }

    @Test
    void shouldExposeStructuredResponseContract() {
        AiInvocationScene scene = AiInvocationScene.VOCABULARY_SCENE_UNIT;

        assertThat(scene.isStructuredResponse()).isTrue();
        assertThat(scene.getRequiredRootFields())
                .containsExactly("title", "learning_text", "translation", "vocabulary");
    }

    @Test
    void shouldSerializeAsBusinessCode() throws Exception {
        assertThat(objectMapper.writeValueAsString(AiInvocationScene.ARTICLE_STUDY_MATERIAL))
                .isEqualTo("\"article_study_material\"");
        assertThat(objectMapper.readValue("\"vocabulary_follow_up\"", AiInvocationScene.class))
                .isEqualTo(AiInvocationScene.VOCABULARY_FOLLOW_UP);
    }

    @Test
    void shouldRejectUnknownScene() {
        assertThatThrownBy(() -> AiInvocationScene.of("unknown"))
                .isInstanceOf(LearningAssistantException.class)
                .hasMessageContaining("不支持的 AI 调用场景");
    }
}
