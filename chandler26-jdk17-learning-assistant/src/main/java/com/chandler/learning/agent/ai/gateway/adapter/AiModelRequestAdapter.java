package com.chandler.learning.agent.ai.gateway.adapter;

import com.chandler.learning.agent.ai.gateway.protocol.ModelChatRequest;
import com.chandler.learning.agent.ai.gateway.protocol.AiRequestAdapterType;
import com.chandler.learning.agent.ai.gateway.protocol.AiPreparedModelRequest;

/**
 * 调用模型前的请求适配器。
 */
public interface AiModelRequestAdapter {

    AiRequestAdapterType type();

    AiPreparedModelRequest prepare(ModelChatRequest request);
}
