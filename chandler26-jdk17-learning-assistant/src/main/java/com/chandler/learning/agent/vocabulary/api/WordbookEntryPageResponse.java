package com.chandler.learning.agent.vocabulary.api;

import lombok.Data;

import java.util.List;

/** 单词本词条分页结果。 */
@Data
public class WordbookEntryPageResponse {

    private List<WordbookEntrySummaryResponse> items;
    private long total;
    private int page;
    private int pageSize;
}
