package com.chandler.learning.agent.domain.dto.learning;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 场景学习计划创建请求。
 */
@Data
public class LearningPlanCreateRequest {

    @NotNull(message = "词表版本不能为空")
    private Long catalogVersionId;

    @NotNull(message = "单词本不能为空")
    private Long wordbookId;

    @NotBlank(message = "计划名称不能为空")
    private String name;

    private String learningPurpose;

    private Long modelConfigId;

    /** 是否在创建后立即生成首个场景，默认 true。 */
    private Boolean generateFirstUnit;
}
