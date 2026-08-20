package com.chandler.learning.agent.ai.model.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chandler.learning.agent.common.persistence.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 可在学习助手中维护的 AI 模型配置。
 */
@Data
@TableName("ai_model_config")
@Schema(name = "AI 模型配置")
public class AiModelConfig extends BaseEntity {

    /**
     * id 属性。
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * name 属性。
     */
    private String name;

    /**
     * provider 属性。
     */
    private String provider;

    /**
     * modelName 属性。
     */
    private String modelName;

    /**
     * baseUrl 属性。
     */
    private String baseUrl;

    /**
     * chatPath 属性。
     */
    private String chatPath;

    /**
     * apiKey 属性。
     */
    private String apiKey;

    /**
     * enabled 属性。
     */
    private Boolean enabled;

    /**
     * isDefault 属性。
     */
    private Boolean isDefault;

    /**
     * sequence 属性。
     */
    private Integer sequence;
}
