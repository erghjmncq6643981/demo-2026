package com.chandler.learning.agent.vocabulary.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * WordbookResponse 类。
 */
@Data
public class WordbookResponse {

    @Schema(description = "主键标识")
    private Long id;

    @Schema(description = "名称")
    private String name;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "是否默认")
    private Boolean isDefault;

    @Schema(description = "数量")
    private Long entryCount;

    @Schema(description = "到期复习数量")
    private Long dueCount;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
