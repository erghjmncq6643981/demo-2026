package com.chandler.learning.agent.domain.dto.vocabulary;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Markdown 词表导入请求。
 */
@Data
public class VocabularyMarkdownImportRequest {

    @NotBlank(message = "词表名称不能为空")
    private String catalogName;

    @Schema(description = "自考、四级、六级、雅思或其它学习目的")
    private String learningPurpose;

    private String examType;

    private String fileName;

    @NotBlank(message = "Markdown 内容不能为空")
    private String content;
}
