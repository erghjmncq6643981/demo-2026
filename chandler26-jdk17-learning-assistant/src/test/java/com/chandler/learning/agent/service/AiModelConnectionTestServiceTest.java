package com.chandler.learning.agent.service;

import com.chandler.learning.agent.domain.dto.AiModelConnectionTestResponse;
import com.chandler.learning.agent.domain.dto.ModelChatRequest;
import com.chandler.learning.agent.domain.dto.ModelChatResponse;
import com.chandler.learning.agent.domain.entity.AiModelConfig;
import com.chandler.learning.agent.domain.enums.AiInvocationScene;
import com.chandler.learning.agent.exception.LearningAssistantException;
import com.chandler.learning.agent.service.learning.SystemLogService;
import com.chandler.learning.agent.service.learning.UserDisplayNameService;
import com.chandler.learning.agent.support.AiModelClient;
import com.chandler.learning.agent.support.LearningConstants;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiModelConnectionTestServiceTest {

    @Test
    void sendsMinimalRequestDirectlyToModelClient() {
        AiModelConfigService modelConfigService = mock(AiModelConfigService.class);
        AiModelClient modelClient = mock(AiModelClient.class);
        AiModelConnectionTestService service = new AiModelConnectionTestService(
                modelConfigService, modelClient, mock(SystemLogService.class), mock(UserDisplayNameService.class));
        when(modelConfigService.getById(11L)).thenReturn(config());
        ModelChatResponse modelResponse = new ModelChatResponse();
        modelResponse.setContent("OK");
        when(modelClient.testConnection(org.mockito.ArgumentMatchers.any())).thenReturn(modelResponse);

        AiModelConnectionTestResponse result = service.test(11L);

        ArgumentCaptor<ModelChatRequest> requestCaptor = ArgumentCaptor.forClass(ModelChatRequest.class);
        verify(modelClient).testConnection(requestCaptor.capture());
        verify(modelClient, never()).chat(org.mockito.ArgumentMatchers.any());
        ModelChatRequest request = requestCaptor.getValue();
        assertThat(request.getInvocationScene()).isEqualTo(AiInvocationScene.MODEL_CONNECTION_TEST);
        assertThat(request.getModelConfigId()).isEqualTo(11L);
        assertThat(request.getMaxTokens()).isEqualTo(16);
        assertThat(request.getMessages()).singleElement()
                .satisfies(message -> assertThat(message.getContent()).isEqualTo("请仅回复 OK"));
        assertThat(result.getSuccess()).isTrue();
        assertThat(result.getResponsePreview()).isEqualTo("OK");
    }

    @Test
    void returnsReadableFailureWithoutThrowingProviderErrorToUi() {
        AiModelConfigService modelConfigService = mock(AiModelConfigService.class);
        AiModelClient modelClient = mock(AiModelClient.class);
        AiModelConnectionTestService service = new AiModelConnectionTestService(
                modelConfigService, modelClient, mock(SystemLogService.class), mock(UserDisplayNameService.class));
        when(modelConfigService.getById(11L)).thenReturn(config());
        when(modelClient.testConnection(org.mockito.ArgumentMatchers.any())).thenThrow(
                LearningAssistantException.externalService(
                        LearningConstants.ErrorCode.AI_MODEL_CALL_FAILED,
                        "API Key 无效",
                        null));

        AiModelConnectionTestResponse result = service.test(11L);

        assertThat(result.getSuccess()).isFalse();
        assertThat(result.getMessage()).isEqualTo("API Key 无效");
        assertThat(result.getProvider()).isEqualTo("deepseek");
        assertThat(result.getModelName()).isEqualTo("deepseek-v4-pro");
    }

    private AiModelConfig config() {
        AiModelConfig config = new AiModelConfig();
        config.setId(11L);
        config.setName("DeepSeek 主模型");
        config.setProvider("deepseek");
        config.setModelName("deepseek-v4-pro");
        config.setEnabled(false);
        return config;
    }
}
