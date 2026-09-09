package com.chandler.learning.agent.ai.gateway.client;

import com.chandler.learning.agent.ai.gateway.adapter.AiModelRequestAdapterRegistry;
import com.chandler.learning.agent.exception.LearningAssistantException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class OpenAiCompatibleModelClientResponseTest {

    @Test
    void reportsLengthLimitBeforeEmptyContentWhenReasoningConsumesAllTokens() {
        OpenAiCompatibleModelClient client = new OpenAiCompatibleModelClient(
                mock(RestTemplate.class),
                mock(com.chandler.learning.agent.ai.model.application.AiModelConfigService.class),
                new ObjectMapper(),
                mock(AiModelRequestAdapterRegistry.class));
        String response = "{\"choices\":[{\"message\":{\"content\":\"\","
                + "\"reasoning_content\":\"需要先思考\"},\"finish_reason\":\"length\"}]}";

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(client, "parseResponse", response))
                .isInstanceOf(LearningAssistantException.class)
                .hasMessage("AI 输出达到长度上限，请减少本次输入后重试");
    }

    @Test
    void acceptsReasoningOnlyResponseForConnectionProbe() {
        OpenAiCompatibleModelClient client = new OpenAiCompatibleModelClient(
                mock(RestTemplate.class),
                mock(com.chandler.learning.agent.ai.model.application.AiModelConfigService.class),
                new ObjectMapper(),
                mock(AiModelRequestAdapterRegistry.class));
        String response = "{\"choices\":[{\"message\":{\"content\":\"\","
                + "\"reasoning_content\":\"链路可用\"},\"finish_reason\":\"length\"}]}";

        Object parsed = ReflectionTestUtils.invokeMethod(client, "parseResponse", response, true);

        assertThat(parsed).isNotNull();
        assertThat(((com.chandler.learning.agent.ai.gateway.protocol.ModelChatResponse) parsed).getContent())
                .isEqualTo("链路可用");
    }
}
