package com.chandler.learning.agent.vocabulary.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Data;

/**
 * VocabularyTagResponse 类。
 */
@Data
public class VocabularyTagResponse {

    @Schema(description = "主键标识")
    private Long id;

    @Schema(description = "业务属性")
    private String tagType;

    @Schema(description = "业务属性")
    private String tagValue;

    @Schema(description = "展示名称")
    private String displayName;

    @Schema(description = "权重")
    private Integer weight;
}
