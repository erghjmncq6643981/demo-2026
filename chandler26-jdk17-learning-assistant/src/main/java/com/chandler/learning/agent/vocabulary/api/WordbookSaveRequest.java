package com.chandler.learning.agent.vocabulary.api;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * WordbookSaveRequest 类。
 */
@Data
public class WordbookSaveRequest {

    private Long id;

    @NotBlank(message = "单词本名称不能为空")
    private String name;

    private String description;

    private Boolean isDefault;
}
