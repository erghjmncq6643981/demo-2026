package com.chandler.learning.agent.vocabulary.api;

import lombok.Data;

/**
 * 用户输入词汇的最接近缓存匹配。
 */
@Data
public class VocabularyBestMatchResponse {

    private String query;

    private String matchedTerm;

    private String normalizedTerm;

    private String partOfSpeech;

    private String meaning;

    private Integer matchScore;

    private String matchType;

    private VocabularyStudyResponse record;
}
