package com.chandler.learning.agent.domain.dto;

import lombok.Data;

/**
 * 普通学习界面使用的可选模型最小信息。
 */
@Data
public class AiModelOptionResponse {

    private Long id;
    private String name;
    private String provider;
    private String modelName;
    private Boolean enabled;
    private Boolean isDefault;
    private Integer sequence;
}
