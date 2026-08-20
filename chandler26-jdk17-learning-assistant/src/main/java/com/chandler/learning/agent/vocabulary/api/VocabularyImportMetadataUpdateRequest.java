package com.chandler.learning.agent.vocabulary.api;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 更新词表导入元数据请求。
 */
@Data
public class VocabularyImportMetadataUpdateRequest {

    @NotBlank(message = "词表名称不能为空")
    private String catalogName;

    private String sourceType;

    private String learningPurpose;
}
