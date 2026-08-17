package com.chandler.learning.agent.domain.dto.learning;

import lombok.Data;

/**
 * 手动触发下一个场景单元请求。
 */
@Data
public class LearningPlanNextUnitRequest {

    private Long modelConfigId;
}
