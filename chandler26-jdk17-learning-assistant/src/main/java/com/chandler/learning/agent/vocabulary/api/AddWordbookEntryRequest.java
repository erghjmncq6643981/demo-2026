package com.chandler.learning.agent.vocabulary.api;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * AddWordbookEntryRequest 类。
 */
@Data
public class AddWordbookEntryRequest {

    @NotBlank(message = "单词不能为空")
    private String term;

    private String note;
}
