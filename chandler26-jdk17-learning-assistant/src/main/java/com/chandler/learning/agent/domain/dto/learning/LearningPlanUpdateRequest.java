package com.chandler.learning.agent.domain.dto.learning;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 场景学习计划更新请求。
 */
@Data
public class LearningPlanUpdateRequest {

    @NotBlank(message = "计划名称不能为空")
    private String name;

    private String learningPurpose;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Long modelConfigId;

    private Long wordbookId;

    private String status;
}
