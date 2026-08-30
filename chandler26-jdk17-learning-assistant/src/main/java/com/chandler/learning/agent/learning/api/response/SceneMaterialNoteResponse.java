package com.chandler.learning.agent.learning.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 场景材料 Markdown 笔记响应。
 */
@Data
public class SceneMaterialNoteResponse {

    @Schema(description = "主键 ID")
    private Long id;

    @Schema(description = "学习计划标识")
    private Long planId;

    @Schema(description = "场景单元标识")
    private Long unitId;

    @Schema(description = "场景材料标识")
    private Long sceneMaterialId;

    @Schema(description = "正文内容")
    private String content;

    @Schema(description = "内容格式")
    private String contentFormat;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
