package com.chandler.learning.agent.domain.dto.learning;

import lombok.Data;

@Data
public class VocabularyRelationResponse {

    private Long id;

    private Long relatedVocabularyId;

    private String relatedTerm;

    private String relationType;

    private String relationValue;

    private String relatedPartOfSpeech;

    private String relatedMeaning;

    private String matchType;

    private Integer matchScore;

    private Integer score;
}
