package com.chandler.learning.agent.domain.dto.learning;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class WordbookEntryTransferRequest {

    @NotNull(message = "目标单词本不能为空")
    private Long targetWordbookId;

    private Boolean copy;
}
