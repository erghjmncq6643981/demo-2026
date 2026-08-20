package com.chandler.learning.agent.vocabulary.api;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * WordbookEntryTransferRequest 类。
 */
@Data
public class WordbookEntryTransferRequest {

    @NotNull(message = "目标单词本不能为空")
    private Long targetWordbookId;

    private Boolean copy;
}
