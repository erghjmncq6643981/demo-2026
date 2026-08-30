package com.chandler.learning.agent.vocabulary.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 可供学习者创建计划的公共词本。
 */
@Data
public class VocabularyCatalogResponse {

    @Schema(description = "公共词本 ID")
    private Long catalogId;

    @Schema(description = "词本版本标识")
    private Long catalogVersionId;

    @Schema(description = "异步任务标识")
    private Long jobId;

    @Schema(description = "公共词本名称")
    private String catalogName;

    @Schema(description = "数据源类型")
    private String sourceType;

    @Schema(description = "学习目标")
    private String learningPurpose;

    @Schema(description = "当前业务状态")
    private String status;

    @Schema(description = "任务或分页数据总数")
    private Integer totalCount;

    @Schema(description = "发布时间")
    private LocalDateTime publishedTime;
}
