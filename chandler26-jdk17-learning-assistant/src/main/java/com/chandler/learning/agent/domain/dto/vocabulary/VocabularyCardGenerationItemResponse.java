package com.chandler.learning.agent.domain.dto.vocabulary;

import lombok.Data;

/**
 * 批量词卡任务单词级结果。
 */
@Data
public class VocabularyCardGenerationItemResponse {

    private Long id;

    private String term;

    private String normalizedTerm;

    private String status;

    private Long vocabularyId;

    private Integer attemptCount;

    private String errorMessage;
}
