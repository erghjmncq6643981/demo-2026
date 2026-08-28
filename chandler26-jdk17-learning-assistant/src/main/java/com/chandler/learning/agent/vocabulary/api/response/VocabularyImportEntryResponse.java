package com.chandler.learning.agent.vocabulary.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Data;

import java.util.List;

/**
 * 词表导入预览词条。
 */
@Data
public class VocabularyImportEntryResponse {

    @Schema(description = "主键标识")
    private Long id;

    @Schema(description = "来源序号")
    private Integer sourceOrder;

    @Schema(description = "原始词汇")
    private String originalTerm;

    @Schema(description = "建议词汇")
    private String suggestedTerm;

    @Schema(description = "确认词汇")
    private String approvedTerm;

    @Schema(description = "生效词汇")
    private String effectiveTerm;

    @Schema(description = "音标")
    private String phonetic;

    @Schema(description = "英文释义")
    private String definition;

    @Schema(description = "是否疑似断词")
    private Boolean suspicious;

    @Schema(description = "状态")
    private String reviewStatus;

    @Schema(description = "疑似问题列表")
    private List<String> warnings;
}
