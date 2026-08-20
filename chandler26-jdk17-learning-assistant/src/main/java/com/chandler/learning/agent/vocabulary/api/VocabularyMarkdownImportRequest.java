package com.chandler.learning.agent.vocabulary.api;

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

    @NotBlank(message = "数据源类型不能为空")
    @Schema(description = "数据源类型：self_study、cet4、cet6、ielts")
    private String sourceType;

    /** 兼容旧客户端；新客户端请使用 sourceType。 */
    private String learningPurpose;

    /** 兼容旧客户端，服务端会优先使用 sourceType。 */
    private String examType;

    private String fileName;

    @NotBlank(message = "Markdown 内容不能为空")
    private String content;
}
