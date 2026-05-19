package com.chandler.learning.agent.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 可在学习助手中维护的 AI 模型配置。
 */
@Data
@TableName("ai_model_config")
@Schema(name = "AI 模型配置")
public class AiModelConfig {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    private String name;

    private String provider;

    private String modelName;

    private String baseUrl;

    private String chatPath;

    private String apiKey;

    private Boolean enabled;

    private Boolean isDefault;

    private Integer sequence;

    private Boolean deleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
