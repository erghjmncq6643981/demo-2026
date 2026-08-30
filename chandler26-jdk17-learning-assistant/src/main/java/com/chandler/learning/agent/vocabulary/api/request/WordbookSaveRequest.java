package com.chandler.learning.agent.vocabulary.api.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 个人单词本请求参数。
 */
@Data
public class WordbookSaveRequest {

    @Schema(description = "主键 ID")
    private Long id;

    @NotBlank(message = "单词本名称不能为空")
    @Schema(description = "业务对象名称")
    private String name;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "是否默认")
    private Boolean isDefault;
}
