package com.chandler.learning.agent.support;

import com.chandler.learning.agent.domain.dto.ModelChatRequest;
import com.chandler.learning.agent.domain.enums.AiRequestAdapterType;

/**
 * 调用模型前的请求适配器。
 */
public interface AiModelRequestAdapter {

    AiRequestAdapterType type();

    AiPreparedModelRequest prepare(ModelChatRequest request);
}
