package com.chandler.learning.agent.support;

import com.chandler.learning.agent.domain.dto.ModelChatRequest;
import com.chandler.learning.agent.domain.enums.AiApiProtocol;
import com.chandler.learning.agent.domain.enums.AiRequestAdapterType;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * DeepSeek Chat Completions 请求预处理链。
 */
@Component
public class DeepSeekChatRequestAdapter extends AbstractOpenAiChatRequestAdapter {

    @Override
    public AiRequestAdapterType type() {
        return AiRequestAdapterType.DEEPSEEK_CHAT;
    }

    @Override
    public AiPreparedModelRequest prepare(ModelChatRequest request) {
        Map<String, Object> payload = textMessagePayload(request);
        putIfPresent(payload, "temperature", request.getTemperature());
        putIfPresent(payload, "max_tokens", request.getMaxTokens());
        applyJsonMode(request, payload);
        if (request.getInvocationScene() != null && request.getInvocationScene().independentAction()) {
            payload.put("thinking", Map.of("type", "disabled"));
        }
        return new AiPreparedModelRequest(AiApiProtocol.OPENAI_CHAT_COMPLETIONS, type(), payload);
    }
}
