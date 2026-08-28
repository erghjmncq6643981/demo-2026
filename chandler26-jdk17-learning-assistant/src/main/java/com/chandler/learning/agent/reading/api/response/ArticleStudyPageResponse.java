package com.chandler.learning.agent.reading.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Data;

import java.util.List;

/** 语境精读历史分页结果。 */
@Data
public class ArticleStudyPageResponse {

    @Schema(description = "列表数据")
    private List<ArticleStudySummaryResponse> items;
    @Schema(description = "总数量")
    private long total;
    @Schema(description = "页码")
    private int page;
    @Schema(description = "每页数量")
    private int pageSize;
}
