package com.chandler.learning.agent.learning.api.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 场景学习计划创建请求。
 */
@Data
public class LearningPlanCreateRequest {

    @jakarta.validation.constraints.NotNull(message = "公共词本不能为空")
    @Schema(description = "公共词本版本 ID")
    private Long catalogVersionId;

    /** 可选个人词本；未传时使用默认个人词本承载学习快照。 */
    @Schema(description = "单词本标识")
    private Long wordbookId;

    @NotBlank(message = "计划名称不能为空")
    @Schema(description = "业务对象名称")
    private String name;

    @Schema(description = "学习目标")
    private String learningPurpose;

    @Schema(description = "开始时间")
    private java.time.LocalDateTime startTime;

    @Schema(description = "结束时间")
    private java.time.LocalDateTime endTime;

    @Schema(description = "模型配置标识")
    private Long modelConfigId;

    /** 是否在创建后立即生成首个场景，默认 true。 */
    @Schema(description = "是否生成首个场景单元")
    private Boolean generateFirstUnit;
}
