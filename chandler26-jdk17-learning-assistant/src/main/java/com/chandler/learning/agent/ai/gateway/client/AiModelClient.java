package com.chandler.learning.agent.ai.gateway.client;

import com.chandler.learning.agent.ai.gateway.protocol.ModelChatRequest;
import com.chandler.learning.agent.ai.gateway.protocol.ModelChatResponse;

/**
 * AI 模型客户端。
 */
public interface AiModelClient {

    ModelChatResponse chat(ModelChatRequest request);

    /**
     * 直接测试模型连接，不创建业务会话或调用记录。
     */
    ModelChatResponse testConnection(ModelChatRequest request);
}
