package com.chandler.learning.agent.vocabulary.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Data;

/**
 * 用户输入词汇的最接近缓存匹配。
 */
@Data
public class VocabularyBestMatchResponse {

    @Schema(description = "查询关键词")
    private String query;

    @Schema(description = "匹配词汇")
    private String matchedTerm;

    @Schema(description = "标准化词汇")
    private String normalizedTerm;

    @Schema(description = "词性")
    private String partOfSpeech;

    @Schema(description = "释义")
    private String meaning;

    @Schema(description = "业务属性")
    private Integer matchScore;

    @Schema(description = "业务属性")
    private String matchType;

    @Schema(description = "业务属性")
    private VocabularyStudyResponse record;
}
