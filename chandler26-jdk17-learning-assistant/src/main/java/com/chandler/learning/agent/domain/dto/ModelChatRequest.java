package com.chandler.learning.agent.domain.dto;

import lombok.Data;

import java.util.List;

/**
 * 模型调用请求。
 */
@Data
public class ModelChatRequest {

    private String provider;

    private String model;

    private Long modelConfigId;

    private Double temperature;

    private Integer maxTokens;

    private List<ChatMessageParam> messages;
}
