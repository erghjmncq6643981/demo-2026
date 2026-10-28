package com.chandler.learning.agent.ai.gateway.adapter;

import com.chandler.learning.agent.ai.gateway.protocol.AiApiProtocol;
import com.chandler.learning.agent.ai.gateway.protocol.AiPreparedModelRequest;
import com.chandler.learning.agent.ai.gateway.protocol.AiRequestAdapterType;
import com.chandler.learning.agent.ai.gateway.protocol.ModelChatRequest;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 通义千问 (Qwen) Chat Completions 请求预处理链。
 */
@Component
public class QwenChatRequestAdapter extends AbstractOpenAiChatRequestAdapter {

    /** 返回通义千问对话请求适配器类型。 */
    @Override
    public AiRequestAdapterType type() {
        return AiRequestAdapterType.QWEN_CHAT;
    }

    /** 根据通义千问模型能力组装请求体。 */
    @Override
    public AiPreparedModelRequest prepare(ModelChatRequest request) {
        Map<String, Object> payload = textMessagePayload(request);
        putIfPresent(payload, "temperature", request.getTemperature());
        putIfPresent(payload, "max_tokens", request.getMaxTokens());
        applyJsonMode(request, payload);
        return new AiPreparedModelRequest(AiApiProtocol.OPENAI_CHAT_COMPLETIONS, type(), payload);
    }
}
