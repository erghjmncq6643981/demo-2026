package com.chandler.learning.agent.vocabulary.api.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 个人单词本请求参数。
 */
@Data
public class AddWordbookEntryRequest {

    @NotBlank(message = "单词不能为空")
    @Schema(description = "英文词汇")
    private String term;

    @Schema(description = "学习笔记")
    private String note;
}
