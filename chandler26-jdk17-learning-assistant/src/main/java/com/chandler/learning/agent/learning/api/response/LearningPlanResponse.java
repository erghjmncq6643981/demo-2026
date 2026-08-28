package com.chandler.learning.agent.learning.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 场景学习计划响应。
 */
@Data
public class LearningPlanResponse {

    @Schema(description = "主键标识")
    private Long id;

    @Schema(description = "公共词本标识")
    private Long catalogId;

    @Schema(description = "词本版本标识")
    private Long catalogVersionId;

    @Schema(description = "单词本标识")
    private Long wordbookId;

    @Schema(description = "名称")
    private String name;

    @Schema(description = "学习目标")
    private String learningPurpose;

    @Schema(description = "开始时间")
    private java.time.LocalDateTime startTime;

    @Schema(description = "结束时间")
    private java.time.LocalDateTime endTime;

    @Schema(description = "状态")
    private String status;

    @Schema(description = "业务属性")
    private Integer totalCatalogWords;

    @Schema(description = "业务属性")
    private Integer learnedCoreWords;

    @Schema(description = "已完成场景单元数量")
    private Integer completedUnitCount;

    @Schema(description = "关联业务标识")
    private Long currentUnitId;

    @Schema(description = "AI 会话标识")
    private Long aiSessionId;

    @Schema(description = "是否满足该条件")
    private Boolean canGenerateNext;

    @Schema(description = "场景单元列表")
    private List<LearningPlanUnitResponse> units;

    @Schema(description = "时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
