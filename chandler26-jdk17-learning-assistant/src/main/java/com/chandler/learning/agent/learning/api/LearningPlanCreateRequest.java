package com.chandler.learning.agent.learning.api;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 场景学习计划创建请求。
 */
@Data
public class LearningPlanCreateRequest {

    @jakarta.validation.constraints.NotNull(message = "公共词本不能为空")
    private Long catalogVersionId;

    /** 可选个人词本；未传时使用默认个人词本承载学习快照。 */
    private Long wordbookId;

    @NotBlank(message = "计划名称不能为空")
    private String name;

    private String learningPurpose;

    private java.time.LocalDateTime startTime;

    private java.time.LocalDateTime endTime;

    private Long modelConfigId;

    /** 是否在创建后立即生成首个场景，默认 true。 */
    private Boolean generateFirstUnit;
}
