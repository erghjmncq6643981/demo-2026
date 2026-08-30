package com.chandler.learning.agent.vocabulary.api.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 个人单词本请求参数。
 */
@Data
public class WordbookEntryTransferRequest {

    @NotNull(message = "目标单词本不能为空")
    @Schema(description = "目标单词本 ID")
    private Long targetWordbookId;

    @Schema(description = "是否复制而非移动")
    private Boolean copy;
}
