package com.chandler.learning.agent.ai.gateway.protocol;

import com.chandler.learning.agent.ai.gateway.protocol.AiApiProtocol;
import com.chandler.learning.agent.ai.gateway.protocol.AiRequestAdapterType;

import java.util.Map;

/**
 * 模型请求适配器生成的传输请求。
 *
 * @param protocol    API 协议
 * @param adapterType 实际请求适配器
 * @param payload     不包含认证信息的 HTTP 请求体
 */
public record AiPreparedModelRequest(AiApiProtocol protocol, AiRequestAdapterType adapterType,
                                     Map<String, Object> payload) {
}
