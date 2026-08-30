package com.chandler.learning.agent.ai.model.domain.entity;

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

    /** 主键 ID。 */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /** 业务对象名称。 */
    private String name;

    /** 模型供应商。 */
    private String provider;

    /** 模型名称。 */
    private String modelName;

    /** 模型服务基础地址。 */
    private String baseUrl;

    /** 模型对话接口路径。 */
    private String chatPath;

    /** 模型 API 密钥。 */
    private String apiKey;

    /** 是否启用。 */
    private Boolean enabled;

    /** 是否为默认配置。 */
    private Boolean isDefault;

    /** 展示或执行顺序。 */
    private Integer sequence;
}
