package com.chandler.learning.agent.learning.api.request;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Data;

import java.time.LocalDate;

/**
 * 手动触发下一个场景单元请求。
 */
@Data
public class LearningPlanNextUnitRequest {

    @Schema(description = "模型配置 ID")
    private Long modelConfigId;

    @Schema(description = "建议学习日期")
    private LocalDate recommendedDate;
}
