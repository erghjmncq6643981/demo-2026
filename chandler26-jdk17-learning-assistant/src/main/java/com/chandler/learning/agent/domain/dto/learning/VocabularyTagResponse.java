package com.chandler.learning.agent.domain.dto.learning;

import lombok.Data;

@Data
public class VocabularyTagResponse {

    private Long id;

    private String tagType;

    private String tagValue;

    private String displayName;

    private Integer weight;
}
