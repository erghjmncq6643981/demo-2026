package com.chandler.learning.agent.domain.dto.learning;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class WordbookSaveRequest {

    private Long id;

    @NotBlank(message = "词书名称不能为空")
    private String name;

    private String description;

    private Boolean isDefault;
}
