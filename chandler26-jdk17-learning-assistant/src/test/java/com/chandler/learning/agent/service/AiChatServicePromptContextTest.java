package com.chandler.learning.agent.service;

import com.chandler.learning.agent.domain.dto.AgentChatRequest;
import com.chandler.learning.agent.domain.dto.ChatMessageParam;
import com.chandler.learning.agent.domain.entity.AiAgent;
import com.chandler.learning.agent.domain.entity.AiChatSession;
import com.chandler.learning.agent.domain.enums.AiInvocationScene;
import com.chandler.learning.agent.exception.LearningAssistantException;
import com.chandler.learning.agent.mapper.AiModelCallRecordMapper;
import com.chandler.learning.agent.service.learning.UserDisplayNameService;
import com.chandler.learning.agent.support.AiModelClient;
import com.chandler.learning.agent.support.PromptRenderer;
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

class AiChatServicePromptContextTest {

    @Test
    void independentActionUsesOnlyDeclaredInputVariablesAndSkipsHistory() {
        AiChatSessionService sessionService = mock(AiChatSessionService.class);
        AiChatService service = new AiChatService(
                mock(AiAgentService.class), mock(AiPromptTemplateService.class), sessionService,
                mock(AiModelConfigService.class), mock(AiModelClient.class),
                new PromptRenderer(new ObjectMapper()), mock(AiModelCallRecordMapper.class),
                new ObjectMapper(), mock(UserDisplayNameService.class));

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
    void rejectsPromptAtNinetyPercentOfEightThousandTokenContext() {
        AiChatService service = serviceWith(mock(AiChatSessionService.class));
        List<ChatMessageParam> messages = List.of(new ChatMessageParam("user", "a".repeat(30_000)));

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                service, "validatePromptBudget", AiInvocationScene.ARTICLE_STUDY_MATERIAL, messages))
                .isInstanceOf(LearningAssistantException.class)
                .hasMessageContaining("7372 Token");
    }

    private AiChatService serviceWith(AiChatSessionService sessionService) {
        return new AiChatService(
                mock(AiAgentService.class), mock(AiPromptTemplateService.class), sessionService,
                mock(AiModelConfigService.class), mock(AiModelClient.class),
                new PromptRenderer(new ObjectMapper()), mock(AiModelCallRecordMapper.class),
                new ObjectMapper(), mock(UserDisplayNameService.class));
    }
}
