package com.chandler.learning.agent.reading.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Data;

import java.util.List;

/** 语境精读历史分页结果。 */
@Data
public class ArticleStudyPageResponse {

    @Schema(description = "分页数据列表")
    private List<ArticleStudySummaryResponse> items;
    @Schema(description = "分页数据总数")
    private long total;
    @Schema(description = "页码")
    private int page;
    @Schema(description = "每页数量")
    private int pageSize;
}
