package com.chandler.learning.agent.ai.gateway.adapter;

import com.chandler.learning.agent.ai.gateway.protocol.ChatMessageParam;
import com.chandler.learning.agent.ai.gateway.protocol.ModelChatRequest;
import com.chandler.learning.agent.ai.chat.domain.AiInvocationScene;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AiModelRequestAdapterTest {

    @Test
    void deepSeekUsesTextContentMaxTokensAndJsonMode() {
        ModelChatRequest request = request("deepseek-v4-pro");
        request.setTemperature(0.55);
        request.setFrequencyPenalty(0.3);

        Map<String, Object> payload = new DeepSeekChatRequestAdapter().prepare(request).payload();

        assertThat(payload).containsEntry("max_tokens", 4096).containsEntry("temperature", 0.55);
        assertThat(payload).doesNotContainKey("frequency_penalty");
        assertThat(payload.get("response_format")).isEqualTo(Map.of("type", "json_object"));
        assertThat(payload.get("thinking")).isEqualTo(Map.of("type", "disabled"));
        assertTextContent(payload);
    }

    @Test
    void kimiK3UsesCompletionTokensAndDoesNotSendFixedSamplingParameters() {
        ModelChatRequest request = request("kimi-k3");
        request.setTemperature(0.55);
        request.setFrequencyPenalty(0.3);
        request.setPresencePenalty(0.1);

        Map<String, Object> payload = new KimiChatRequestAdapter().prepare(request).payload();

        assertThat(payload).containsEntry("max_completion_tokens", 4096)
                .containsEntry("reasoning_effort", "high");
        assertThat(payload).doesNotContainKeys("max_tokens", "temperature", "frequency_penalty",
                "presence_penalty", "thinking");
        assertTextContent(payload);
    }

    @Test
    void kimiK2DisablesThinkingForIndependentJsonAction() {
        ModelChatRequest request = request("kimi-k2.6");

        Map<String, Object> payload = new KimiChatRequestAdapter().prepare(request).payload();

        assertThat(payload.get("thinking")).isEqualTo(Map.of("type", "disabled"));
        assertThat(payload).doesNotContainKey("reasoning_effort");
    }

    @Test
    void kimiK3UsesLowReasoningForConnectionTest() {
        ModelChatRequest request = request("kimi-k3");
        request.setInvocationScene(AiInvocationScene.MODEL_CONNECTION_TEST);
        request.setMaxTokens(16);

        Map<String, Object> payload = new KimiChatRequestAdapter().prepare(request).payload();

        assertThat(payload).containsEntry("max_completion_tokens", 16)
                .containsEntry("reasoning_effort", "low");
        assertThat(payload).doesNotContainKey("response_format");
    }

    private ModelChatRequest request(String model) {
        ModelChatRequest request = new ModelChatRequest();
        request.setModel(model);
        request.setInvocationScene(AiInvocationScene.VOCABULARY_SCENE_UNIT);
        request.setMaxTokens(4096);
        request.setMessages(List.of(new ChatMessageParam("system", "只输出 JSON"),
                new ChatMessageParam("user", "生成学习场景")));
        return request;
    }

    @SuppressWarnings("unchecked")
    private void assertTextContent(Map<String, Object> payload) {
        List<Map<String, String>> messages = (List<Map<String, String>>) payload.get("messages");
        assertThat(messages).extracting(item -> item.get("content"))
                .containsExactly("只输出 JSON", "生成学习场景");
    }
}
