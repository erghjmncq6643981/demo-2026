package com.chandler.learning.agent.domain.dto.vocabulary;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 疑似断词人工修正请求。
 */
@Data
public class VocabularyImportEntryUpdateRequest {

    @NotBlank(message = "确认词不能为空")
    private String approvedTerm;
}
