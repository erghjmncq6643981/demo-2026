package com.chandler.learning.agent.domain.dto.learning;

import lombok.Data;

/**
 * 单词本词条更新请求。
 */
@Data
public class WordbookEntryUpdateRequest {

    private String note;

    private String status;
}
