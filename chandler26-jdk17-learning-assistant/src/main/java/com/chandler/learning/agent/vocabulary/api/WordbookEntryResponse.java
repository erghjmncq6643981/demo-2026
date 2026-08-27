package com.chandler.learning.agent.vocabulary.api;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * WordbookEntryResponse 类。
 */
@Data
public class WordbookEntryResponse {

    private Long id;

    private Long wordbookId;

    private Long vocabularyId;

    private Long progressId;

    private Long catalogEntryId;

    private String term;

    private String normalizedTerm;

    private String note;

    private String status;

    private Integer reviewStage;

    private Integer masteryScore;

    private LocalDateTime lastReviewTime;

    private LocalDateTime nextReviewTime;

    private Integer reviewCount;

    private Integer correctCount;

    private Integer wrongCount;

    private LocalDateTime createTime;

    @com.fasterxml.jackson.annotation.JsonRawValue
    private String parsed;

    private String snapshotProvider;

    private String snapshotModelName;

    private Long snapshotSessionId;

    private LocalDateTime snapshotTime;

    private String cardStatus;

    private String cardErrorMessage;

    private LocalDateTime cardGeneratedTime;

    private List<VocabularyTagResponse> tags;

    private List<VocabularyRelationResponse> relations;
}
