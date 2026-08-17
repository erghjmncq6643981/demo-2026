package com.chandler.learning.agent.domain.dto.learning;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 场景学习计划响应。
 */
@Data
public class LearningPlanResponse {

    private Long id;

    private Long catalogId;

    private Long catalogVersionId;

    private Long wordbookId;

    private String name;

    private String learningPurpose;

    private String status;

    private Integer totalCatalogWords;

    private Integer learnedCoreWords;

    private Integer completedUnitCount;

    private Long currentUnitId;

    private Long aiSessionId;

    private Boolean canGenerateNext;

    private List<LearningPlanUnitResponse> units;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
