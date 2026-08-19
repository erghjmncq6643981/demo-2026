package com.chandler.learning.agent.ai.chat.application;

import com.chandler.learning.agent.ai.chat.application.AgentChatRequest;
import com.chandler.learning.agent.ai.agent.application.AiAgentService;
import com.chandler.learning.agent.ai.model.application.AiModelConfigService;
import com.chandler.learning.agent.ai.prompt.application.AiPromptTemplateService;
import com.chandler.learning.agent.ai.chat.application.codec.AiSceneResponseCodecRegistry;
import com.chandler.learning.agent.ai.gateway.protocol.ChatMessageParam;
import com.chandler.learning.agent.ai.agent.domain.AiAgent;
import com.chandler.learning.agent.ai.chat.domain.AiChatSession;
import com.chandler.learning.agent.ai.model.domain.AiModelConfig;
import com.chandler.learning.agent.ai.chat.domain.AiInvocationScene;
import com.chandler.learning.agent.ai.model.domain.AiModelDefinition;
import com.chandler.learning.agent.exception.LearningAssistantException;
import com.chandler.learning.agent.ai.chat.infrastructure.AiModelCallRecordMapper;
import com.chandler.learning.agent.service.learning.UserDisplayNameService;
import com.chandler.learning.agent.ai.gateway.client.AiModelClient;
import com.chandler.learning.agent.ai.gateway.protocol.AiModelCapabilityResolver;
import com.chandler.learning.agent.ai.gateway.parser.AiStructuredResponseParserRegistry;
import com.chandler.learning.agent.ai.prompt.application.PromptRenderer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiChatServicePromptContextTest {

    @Test
    void independentActionUsesOnlyDeclaredInputVariablesAndSkipsHistory() {
        AiChatSessionService sessionService = mock(AiChatSessionService.class);
        AiChatService service = new AiChatService(
                mock(AiAgentService.class), mock(AiPromptTemplateService.class), sessionService,
                mock(AiModelConfigService.class), mock(AiModelClient.class),
                new PromptRenderer(new ObjectMapper()), mock(AiModelCallRecordMapper.class),
                new ObjectMapper(), mock(UserDisplayNameService.class),
                new AiModelCapabilityResolver(), new AiStructuredResponseParserRegistry(new ObjectMapper()),
                new AiSceneResponseCodecRegistry(new ObjectMapper()), mock(AiCallMetrics.class));

        AiAgent agent = new AiAgent();
        agent.setSystemPrompt("只使用 {{term}} 生成词卡");
        agent.setConcisePrompt("不要使用历史");
        AgentChatRequest request = new AgentChatRequest();
        request.setMessage("生成词卡");
        request.setTemplateCode(null);
        request.setVariables(new HashMap<>(Map.of(
                "term", "abandon",
                "history", "一段不应发送给模型的历史文章",
                "old_words", List.of("irrelevant", "context"))));

        AiChatSession session = new AiChatSession();
        session.setId(7L);
        @SuppressWarnings("unchecked")
        List<ChatMessageParam> messages = ReflectionTestUtils.invokeMethod(
                service, "buildMessages", agent, request, session, AiInvocationScene.VOCABULARY_CARD_SINGLE);

        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).getContent()).contains("abandon").doesNotContain("历史文章");
        assertThat(messages.get(1).getContent()).isEqualTo("生成词卡");
        verify(sessionService, never()).getHistory(7L);
    }

    @Test
    void sceneGenerationReusesSessionForAuditWithoutSendingPreviousMaterials() {
        AiChatSessionService sessionService = mock(AiChatSessionService.class);
        AiChatService service = serviceWith(sessionService);

        AiAgent agent = new AiAgent();
        agent.setSystemPrompt("仅使用本批候选词 {{candidate_words}} 生成场景材料");
        agent.setConcisePrompt("延续历史场景生成材料");
        AgentChatRequest request = new AgentChatRequest();
        request.setMessage("生成当前场景材料");
        request.setVariables(new HashMap<>(Map.of(
                "candidate_words", List.of("airport", "boarding"),
                "completed_scenes", "数月计划中已经生成的大量历史场景材料")));

        AiChatSession session = new AiChatSession();
        session.setId(99L);
        @SuppressWarnings("unchecked")
        List<ChatMessageParam> messages = ReflectionTestUtils.invokeMethod(
                service, "buildMessages", agent, request, session, AiInvocationScene.VOCABULARY_SCENE_UNIT);

        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).getContent())
                .contains("airport", "boarding")
                .doesNotContain("历史场景材料");
        assertThat(messages.get(1).getContent()).isEqualTo("生成当前场景材料");
        verify(sessionService, never()).getHistory(99L);
    }

    @Test
    void rejectsPromptAtNinetyPercentOfSelectedModelContext() {
        AiChatService service = serviceWith(mock(AiChatSessionService.class));
        List<ChatMessageParam> messages = List.of(new ChatMessageParam("user", "a".repeat(30_000)));

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                service, "validatePromptBudget", AiInvocationScene.ARTICLE_STUDY_MATERIAL,
                AiModelDefinition.DEEPSEEK_V4_PRO, 943_600, 1))
                .isInstanceOf(LearningAssistantException.class)
                .hasMessageContaining("943718 Token");
    }

    @Test
    void usesAgentModelBindingUnlessTaskExplicitlyOverridesIt() {
        AiModelConfigService modelConfigService = mock(AiModelConfigService.class);
        AiChatService service = new AiChatService(
                mock(AiAgentService.class), mock(AiPromptTemplateService.class), mock(AiChatSessionService.class),
                modelConfigService, mock(AiModelClient.class), new PromptRenderer(new ObjectMapper()),
                mock(AiModelCallRecordMapper.class), new ObjectMapper(), mock(UserDisplayNameService.class),
                new AiModelCapabilityResolver(), new AiStructuredResponseParserRegistry(new ObjectMapper()),
                new AiSceneResponseCodecRegistry(new ObjectMapper()), mock(AiCallMetrics.class));
        AiAgent agent = new AiAgent();
        agent.setModelConfigId(101L);
        AiModelConfig bound = new AiModelConfig();
        bound.setId(101L);
        AiModelConfig override = new AiModelConfig();
        override.setId(202L);
        when(modelConfigService.requireEnabled(101L)).thenReturn(bound);
        when(modelConfigService.requireEnabled(202L)).thenReturn(override);

        AiModelConfig defaultResult = ReflectionTestUtils.invokeMethod(
                service, "resolveSelectedModelConfig", agent, null);
        AiModelConfig overrideResult = ReflectionTestUtils.invokeMethod(
                service, "resolveSelectedModelConfig", agent, 202L);

        assertThat(defaultResult).isSameAs(bound);
        assertThat(overrideResult).isSameAs(override);
        verify(modelConfigService).requireEnabled(101L);
        verify(modelConfigService).requireEnabled(202L);
    }

    @Test
    void rejectsConnectionTestSceneThroughAgent() {
        AiChatService service = serviceWith(mock(AiChatSessionService.class));
        AgentChatRequest request = new AgentChatRequest();
        request.setInvocationScene(AiInvocationScene.MODEL_CONNECTION_TEST);

        assertThatThrownBy(() -> service.chat(request))
                .isInstanceOf(LearningAssistantException.class)
                .hasMessageContaining("不能通过 Agent 调用");
    }

    private AiChatService serviceWith(AiChatSessionService sessionService) {
        return new AiChatService(
                mock(AiAgentService.class), mock(AiPromptTemplateService.class), sessionService,
                mock(AiModelConfigService.class), mock(AiModelClient.class),
                new PromptRenderer(new ObjectMapper()), mock(AiModelCallRecordMapper.class),
                new ObjectMapper(), mock(UserDisplayNameService.class),
                new AiModelCapabilityResolver(), new AiStructuredResponseParserRegistry(new ObjectMapper()),
                new AiSceneResponseCodecRegistry(new ObjectMapper()), mock(AiCallMetrics.class));
    }
}
