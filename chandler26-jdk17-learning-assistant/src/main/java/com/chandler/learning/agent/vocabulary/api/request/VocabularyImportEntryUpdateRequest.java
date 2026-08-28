package com.chandler.learning.agent.vocabulary.api.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 疑似断词人工修正请求。
 */
@Data
public class VocabularyImportEntryUpdateRequest {

    @NotBlank(message = "确认词不能为空")
    @Schema(description = "确认词汇")
    private String approvedTerm;
}
