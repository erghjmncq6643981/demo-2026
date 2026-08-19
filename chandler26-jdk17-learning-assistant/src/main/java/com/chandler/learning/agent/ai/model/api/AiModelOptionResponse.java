package com.chandler.learning.agent.ai.model.api;

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
    /** 模型展示名称。 */
    private String modelDisplayName;
    /** 模型原生上下文窗口，单位为 Token。 */
    private Integer contextWindowTokens;
    private Boolean enabled;
    private Boolean isDefault;
    private Integer sequence;
}
