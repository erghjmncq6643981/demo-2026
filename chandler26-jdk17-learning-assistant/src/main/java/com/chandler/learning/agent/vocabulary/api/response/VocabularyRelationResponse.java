package com.chandler.learning.agent.vocabulary.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Data;

/**
 * 词汇响应数据。
 */
@Data
public class VocabularyRelationResponse {

    @Schema(description = "主键 ID")
    private Long id;

    @Schema(description = "关联词汇记录 ID")
    private Long relatedVocabularyId;

    @Schema(description = "关联词汇")
    private String relatedTerm;

    @Schema(description = "词汇关系类型")
    private String relationType;

    @Schema(description = "关联值")
    private String relationValue;

    @Schema(description = "关联词词性")
    private String relatedPartOfSpeech;

    @Schema(description = "关联词中文含义")
    private String relatedMeaning;

    @Schema(description = "关联词英式音标")
    private String relatedPhoneticUk;

    @Schema(description = "关联词美式音标")
    private String relatedPhoneticUs;

    @Schema(description = "匹配类型")
    private String matchType;

    @Schema(description = "匹配得分")
    private Integer matchScore;

    @Schema(description = "得分")
    private Integer score;
}
