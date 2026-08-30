package com.chandler.learning.agent.vocabulary.api.request;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Data;

/**
 * 单词本词条更新请求。
 */
@Data
public class WordbookEntryUpdateRequest {

    @Schema(description = "学习笔记")
    private String note;

    @Schema(description = "当前业务状态")
    private String status;
}
