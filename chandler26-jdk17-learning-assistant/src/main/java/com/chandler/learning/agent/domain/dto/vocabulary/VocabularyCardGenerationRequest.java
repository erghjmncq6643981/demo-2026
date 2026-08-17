package com.chandler.learning.agent.domain.dto.vocabulary;

import lombok.Data;

/**
 * 按当前或近期场景生成词卡请求。
 */
@Data
public class VocabularyCardGenerationRequest {

    private Integer batchSize;

    private Long modelConfigId;
}
