package com.chandler.learning.agent.vocabulary.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Data;

/**
 * 词汇响应数据。
 */
@Data
public class VocabularyTagResponse {

    @Schema(description = "主键 ID")
    private Long id;

    @Schema(description = "标签类型")
    private String tagType;

    @Schema(description = "标签值")
    private String tagValue;

    @Schema(description = "展示名称")
    private String displayName;

    @Schema(description = "权重")
    private Integer weight;
}
