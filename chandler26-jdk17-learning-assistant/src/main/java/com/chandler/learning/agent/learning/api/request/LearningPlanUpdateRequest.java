package com.chandler.learning.agent.learning.api.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 场景学习计划更新请求。
 */
@Data
public class LearningPlanUpdateRequest {

    @NotBlank(message = "计划名称不能为空")
    @Schema(description = "名称")
    private String name;

    @Schema(description = "学习目标")
    private String learningPurpose;

    @Schema(description = "开始时间")
    private LocalDateTime startTime;

    @Schema(description = "结束时间")
    private LocalDateTime endTime;

    @Schema(description = "关联业务标识")
    private Long modelConfigId;

    @Schema(description = "单词本标识")
    private Long wordbookId;

    @Schema(description = "业务状态")
    private String status;
}
