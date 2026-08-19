package com.chandler.learning.agent.support;

import com.chandler.learning.agent.domain.dto.ModelChatRequest;
import com.chandler.learning.agent.domain.enums.AiApiProtocol;
import com.chandler.learning.agent.domain.enums.AiInvocationScene;
import com.chandler.learning.agent.domain.enums.AiRequestAdapterType;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Kimi Chat Completions 请求预处理链。
 */
@Component
public class KimiChatRequestAdapter extends AbstractOpenAiChatRequestAdapter {

    @Override
    public AiRequestAdapterType type() {
        return AiRequestAdapterType.KIMI_CHAT;
    }

    @Override
    public AiPreparedModelRequest prepare(ModelChatRequest request) {
        Map<String, Object> payload = textMessagePayload(request);
        if ("kimi-k3".equalsIgnoreCase(request.getModel())) {
            putIfPresent(payload, "max_completion_tokens", request.getMaxTokens());
            payload.put("reasoning_effort",
                    request.getInvocationScene() == AiInvocationScene.MODEL_CONNECTION_TEST ? "low" : "high");
        } else {
            putIfPresent(payload, "max_tokens", request.getMaxTokens());
            if (request.getInvocationScene() != null && request.getInvocationScene().independentAction()) {
                payload.put("thinking", Map.of("type", "disabled"));
            }
        }
        applyJsonMode(request, payload);
        return new AiPreparedModelRequest(AiApiProtocol.OPENAI_CHAT_COMPLETIONS, type(), payload);
    }
}
