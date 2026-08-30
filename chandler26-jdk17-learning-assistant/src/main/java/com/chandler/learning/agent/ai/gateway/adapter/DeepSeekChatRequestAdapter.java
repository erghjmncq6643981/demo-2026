package com.chandler.learning.agent.ai.gateway.adapter;

import com.chandler.learning.agent.ai.gateway.protocol.ModelChatRequest;
import com.chandler.learning.agent.ai.gateway.protocol.AiApiProtocol;
import com.chandler.learning.agent.ai.gateway.protocol.AiRequestAdapterType;
import com.chandler.learning.agent.ai.gateway.protocol.AiPreparedModelRequest;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * DeepSeek Chat Completions 请求预处理链。
 */
@Component
public class DeepSeekChatRequestAdapter extends AbstractOpenAiChatRequestAdapter {

    /** 返回 DeepSeek 对话请求适配器类型。 */
    @Override
    public AiRequestAdapterType type() {
        return AiRequestAdapterType.DEEPSEEK_CHAT;
    }

    /** 根据 DeepSeek 模型能力组装请求体，并为独立动作关闭思考输出。 */
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
