package com.chandler.learning.agent.domain.dto.learning;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AddWordbookEntryRequest {

    @NotBlank(message = "单词不能为空")
    private String term;

    private String note;
}
