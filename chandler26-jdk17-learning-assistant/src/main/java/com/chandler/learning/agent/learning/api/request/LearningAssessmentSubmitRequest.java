package com.chandler.learning.agent.learning.api.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 场景词汇检查提交请求。
 */
@Data
public class LearningAssessmentSubmitRequest {

    @NotNull(message = "单元词汇不能为空")
    @Schema(description = "场景单元词条 ID")
    private Long unitEntryId;

    @NotBlank(message = "检查类型不能为空")
    @Schema(description = "检查类型")
    private String assessmentType;

    @NotBlank(message = "答案不能为空")
    @Schema(description = "作答内容")
    private String answer;

    @Schema(description = "提示等级")
    private Integer hintLevel;

    @Schema(description = "尝试次数")
    private Integer attemptCount;

    @Schema(description = "耗时（毫秒）")
    private Long durationMillis;
}
