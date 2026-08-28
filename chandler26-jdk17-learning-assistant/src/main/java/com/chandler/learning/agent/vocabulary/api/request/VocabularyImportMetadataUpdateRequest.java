package com.chandler.learning.agent.vocabulary.api.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 更新词表导入元数据请求。
 */
@Data
public class VocabularyImportMetadataUpdateRequest {

    @NotBlank(message = "词表名称不能为空")
    @Schema(description = "名称")
    private String catalogName;

    @Schema(description = "数据源类型")
    private String sourceType;

    @Schema(description = "学习目标")
    private String learningPurpose;
}
