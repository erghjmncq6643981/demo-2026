package com.chandler.learning.agent.domain.dto;

import com.chandler.learning.agent.domain.enums.AiInvocationScene;
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

    private Long modelConfigId;

    private Double temperature;

    private Double frequencyPenalty;

    private Double presencePenalty;

    private Integer maxTokens;

    private List<ChatMessageParam> messages;
}
