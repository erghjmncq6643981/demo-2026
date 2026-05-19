package com.chandler.learning.agent.domain.dto.vocabulary;

import com.chandler.learning.agent.domain.dto.learning.VocabularyRelationResponse;
import com.chandler.learning.agent.domain.dto.learning.VocabularyTagResponse;
import lombok.Data;

import java.util.List;

/**
 * 英语词汇学习响应。
 */
@Data
public class VocabularyStudyResponse {

    private Long id;

    private String term;

    private String normalizedTerm;

    private Boolean cacheHit;

    private String agentCode;

    private String templateCode;

    private String provider;

    private String modelName;

    private Long sessionId;

    private String rawContent;

    private Object parsed;

    private Integer tokenUsage;

    private Long costTime;

    private Integer lookupCount;

    private List<VocabularyTagResponse> tags;

    private List<VocabularyRelationResponse> relations;
}
