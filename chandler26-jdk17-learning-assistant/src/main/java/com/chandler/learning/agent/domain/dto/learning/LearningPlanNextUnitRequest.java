package com.chandler.learning.agent.domain.dto.learning;

import lombok.Data;

import java.time.LocalDate;

/**
 * 手动触发下一个场景单元请求。
 */
@Data
public class LearningPlanNextUnitRequest {

    private Long modelConfigId;

    private LocalDate recommendedDate;
}
