package com.chandler.learning.agent.domain.dto.learning;

import lombok.Data;

/**
 * VocabularyTagResponse 类。
 */
@Data
public class VocabularyTagResponse {

    private Long id;

    private String tagType;

    private String tagValue;

    private String displayName;

    private Integer weight;
}
