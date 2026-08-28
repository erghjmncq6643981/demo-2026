package com.chandler.learning.agent.vocabulary.api.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * WordbookEntryTransferRequest 类。
 */
@Data
public class WordbookEntryTransferRequest {

    @NotNull(message = "目标单词本不能为空")
    @Schema(description = "关联业务标识")
    private Long targetWordbookId;

    @Schema(description = "业务属性")
    private Boolean copy;
}
