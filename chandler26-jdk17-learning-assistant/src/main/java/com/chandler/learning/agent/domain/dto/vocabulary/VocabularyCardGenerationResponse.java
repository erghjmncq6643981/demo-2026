package com.chandler.learning.agent.domain.dto.vocabulary;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 批量词卡任务结果。
 */
@Data
public class VocabularyCardGenerationResponse {

    private Long jobId;

    private Long planId;

    private Long unitId;

    private String status;

    private Integer batchSize;

    private Integer totalCount;

    private Integer successCount;

    private Integer failedCount;

    private List<VocabularyCardGenerationItemResponse> items;

    private LocalDateTime startedTime;

    private LocalDateTime finishedTime;
}
