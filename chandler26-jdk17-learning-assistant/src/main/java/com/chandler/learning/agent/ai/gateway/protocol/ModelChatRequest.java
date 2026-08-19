package com.chandler.learning.agent.ai.gateway.protocol;

import com.chandler.learning.agent.ai.chat.domain.AiInvocationScene;
import com.chandler.learning.agent.ai.gateway.protocol.AiApiProtocol;
import com.chandler.learning.agent.ai.gateway.protocol.AiRequestAdapterType;
import com.chandler.learning.agent.ai.gateway.protocol.AiResponseParserType;
import lombok.Data;

import java.util.List;

/**
 * 模型调用请求。
 */
@Data
public class ModelChatRequest {

    private AiInvocationScene invocationScene;

    private String provider;

    private String model;

    /** 本次调用使用的 API 协议，由模型枚举确定。 */
    private AiApiProtocol apiProtocol;

    /** 调用前请求预处理适配器。 */
    private AiRequestAdapterType requestAdapter;

    /** 调用后模型正文解析器。 */
    private AiResponseParserType responseParser;

    /** 当前模型原生上下文窗口，单位为 Token。 */
    private Integer modelContextWindowTokens;

    /** 本次调用实际使用的上下文窗口，单位为 Token。 */
    private Integer effectiveContextWindowTokens;

    private Long modelConfigId;

    private Double temperature;

    private Double frequencyPenalty;

    private Double presencePenalty;

    private Integer maxTokens;

    private List<ChatMessageParam> messages;
}
