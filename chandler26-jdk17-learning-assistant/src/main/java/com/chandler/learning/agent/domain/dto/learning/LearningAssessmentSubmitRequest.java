package com.chandler.learning.agent.domain.dto.learning;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 场景词汇检查提交请求。
 */
@Data
public class LearningAssessmentSubmitRequest {

    @NotNull(message = "单元词汇不能为空")
    private Long unitEntryId;

    @NotBlank(message = "检查类型不能为空")
    private String assessmentType;

    @NotBlank(message = "答案不能为空")
    private String answer;

    private Integer hintLevel;

    private Integer attemptCount;

    private Long durationMillis;
}
