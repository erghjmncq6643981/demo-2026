package com.chandler.learning.agent.domain.dto.vocabulary;

import lombok.Data;

import java.util.List;

/**
 * 批量确认疑似断词请求。
 */
@Data
public class VocabularyImportBatchConfirmRequest {

    private List<Long> entryIds;

    /** 为空或 true 时使用建议词；false 时保留原词。 */
    private Boolean applySuggested;
}
