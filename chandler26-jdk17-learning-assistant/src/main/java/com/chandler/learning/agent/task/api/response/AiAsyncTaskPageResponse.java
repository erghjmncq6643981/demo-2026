package com.chandler.learning.agent.task.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Data;

import java.util.List;

/**
 * AI 异步任务分页结果。
 */
@Data
public class AiAsyncTaskPageResponse {

    @Schema(description = "分页数据列表")
    private List<AiAsyncTaskResponse> items;
    @Schema(description = "分页数据总数")
    private long total;
    @Schema(description = "页码")
    private int page;
    @Schema(description = "每页数量")
    private int pageSize;
}
