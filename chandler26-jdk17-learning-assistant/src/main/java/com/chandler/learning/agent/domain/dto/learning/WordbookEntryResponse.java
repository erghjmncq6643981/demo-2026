package com.chandler.learning.agent.domain.dto.learning;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class WordbookEntryResponse {

    private Long id;

    private Long wordbookId;

    private Long vocabularyId;

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

    private Object parsed;

    private String snapshotProvider;

    private String snapshotModelName;

    private Long snapshotSessionId;

    private LocalDateTime snapshotTime;

    private List<VocabularyTagResponse> tags;

    private List<VocabularyRelationResponse> relations;
}
