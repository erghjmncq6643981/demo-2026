package com.chandler.learning.agent.vocabulary.api.request;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Data;

import java.util.List;

/**
 * 批量确认疑似断词请求。
 */
@Data
public class VocabularyImportBatchConfirmRequest {

    @Schema(description = "词条标识列表")
    private List<Long> entryIds;

    /** 为空或 true 时使用建议词；false 时保留原词。 */
    @Schema(description = "是否采用系统建议词")
    private Boolean applySuggested;
}
