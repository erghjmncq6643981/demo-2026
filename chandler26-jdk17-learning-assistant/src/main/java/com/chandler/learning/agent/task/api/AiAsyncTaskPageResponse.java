package com.chandler.learning.agent.task.api;

import lombok.Data;

import java.util.List;

/**
 * AI 异步任务分页结果。
 */
@Data
public class AiAsyncTaskPageResponse {

    private List<AiAsyncTaskResponse> items;
    private long total;
    private int page;
    private int pageSize;
}
