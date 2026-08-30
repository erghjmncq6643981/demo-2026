package com.chandler.learning.agent.vocabulary.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 个人单词本响应数据。
 */
@Data
public class WordbookResponse {

    @Schema(description = "主键 ID")
    private Long id;

    @Schema(description = "业务对象名称")
    private String name;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "是否默认")
    private Boolean isDefault;

    @Schema(description = "单词本词条数量")
    private Long entryCount;

    @Schema(description = "到期复习数量")
    private Long dueCount;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
