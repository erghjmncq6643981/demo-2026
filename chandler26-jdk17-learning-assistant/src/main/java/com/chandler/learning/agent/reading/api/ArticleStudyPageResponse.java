package com.chandler.learning.agent.reading.api;

import lombok.Data;

import java.util.List;

/** 语境精读历史分页结果。 */
@Data
public class ArticleStudyPageResponse {

    private List<ArticleStudySummaryResponse> items;
    private long total;
    private int page;
    private int pageSize;
}
