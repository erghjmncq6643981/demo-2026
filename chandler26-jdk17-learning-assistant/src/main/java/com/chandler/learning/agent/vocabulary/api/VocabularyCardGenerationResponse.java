package com.chandler.learning.agent.vocabulary.api;

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

    private Long asyncTaskId;

    private String status;

    private Integer batchSize;

    private Integer totalCount;

    private Integer successCount;

    private Integer failedCount;

    /** 任务级失败原因；单词级失败原因位于 items。 */
    private String errorMessage;

    private List<VocabularyCardGenerationItemResponse> items;

    private LocalDateTime startedTime;

    private LocalDateTime finishedTime;
}
