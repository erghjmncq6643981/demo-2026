package com.chandler.learning.agent.support;

import com.chandler.learning.agent.domain.dto.ModelChatRequest;
import com.chandler.learning.agent.domain.dto.ModelChatResponse;

/**
 * AI 模型客户端。
 */
public interface AiModelClient {

    ModelChatResponse chat(ModelChatRequest request);
}
