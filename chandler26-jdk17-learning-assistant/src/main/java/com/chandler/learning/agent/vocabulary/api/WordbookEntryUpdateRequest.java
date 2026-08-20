package com.chandler.learning.agent.vocabulary.api;

import lombok.Data;

/**
 * 单词本词条更新请求。
 */
@Data
public class WordbookEntryUpdateRequest {

    private String note;

    private String status;
}
