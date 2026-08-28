package com.chandler.learning.agent.vocabulary.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Data;

/**
 * VocabularyRelationResponse 类。
 */
@Data
public class VocabularyRelationResponse {

    @Schema(description = "主键标识")
    private Long id;

    @Schema(description = "关联业务标识")
    private Long relatedVocabularyId;

    @Schema(description = "业务属性")
    private String relatedTerm;

    @Schema(description = "业务属性")
    private String relationType;

    @Schema(description = "关联值")
    private String relationValue;

    @Schema(description = "业务属性")
    private String relatedPartOfSpeech;

    @Schema(description = "业务属性")
    private String relatedMeaning;

    @Schema(description = "业务属性")
    private String relatedPhoneticUk;

    @Schema(description = "关联词美式音标")
    private String relatedPhoneticUs;

    @Schema(description = "业务属性")
    private String matchType;

    @Schema(description = "业务属性")
    private Integer matchScore;

    @Schema(description = "得分")
    private Integer score;
}
