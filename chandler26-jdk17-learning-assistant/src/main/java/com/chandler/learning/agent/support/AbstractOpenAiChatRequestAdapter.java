package com.chandler.learning.agent.support;

import com.chandler.learning.agent.domain.dto.ChatMessageParam;
import com.chandler.learning.agent.domain.dto.ModelChatRequest;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI Chat Completions 协议的公共请求构造逻辑。
 */
abstract class AbstractOpenAiChatRequestAdapter implements AiModelRequestAdapter {

    protected Map<String, Object> textMessagePayload(ModelChatRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", request.getModel());
        payload.put("messages", toMessagePayload(request.getMessages()));
        return payload;
    }

    protected void putIfPresent(Map<String, Object> payload, String key, Object value) {
        if (value != null) {
            payload.put(key, value);
        }
    }

    protected void applyJsonMode(ModelChatRequest request, Map<String, Object> payload) {
        if (request.getInvocationScene() != null && request.getInvocationScene().isStructuredResponse()) {
            payload.put("response_format", Map.of("type", "json_object"));
        }
    }

    private List<Map<String, String>> toMessagePayload(List<ChatMessageParam> messages) {
        return messages.stream()
                .map(message -> Map.of("role", message.getRole(), "content", message.getContent()))
                .toList();
    }

}
