package com.chandler.learning.agent.vocabulary.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 词表导入任务及分页预览。
 */
@Data
public class VocabularyImportResponse {

    @Schema(description = "处理任务 ID")
    private Long jobId;

    @Schema(description = "公共词本标识")
    private Long catalogId;

    @Schema(description = "词本版本标识")
    private Long catalogVersionId;

    /** 执行导入的管理员用户 ID。 */
    @Schema(description = "导入人用户标识")
    private Long importerUserId;

    /** 执行导入的管理员显示名称。 */
    @Schema(description = "导入人名称")
    private String importerName;

    @Schema(description = "公共词本名称")
    private String catalogName;

    @Schema(description = "学习目标")
    private String learningPurpose;

    @Schema(description = "数据源类型")
    private String sourceType;

    @Schema(description = "文件名称")
    private String fileName;

    @Schema(description = "当前业务状态")
    private String status;

    @Schema(description = "任务或分页数据总数")
    private Integer totalCount;

    @Schema(description = "疑似问题数量")
    private Integer warningCount;

    @Schema(description = "已确认问题数量")
    private Integer reviewedWarningCount;

    @Schema(description = "待确认问题数量")
    private Integer pendingWarningCount;

    @Schema(description = "页码")
    private Integer page;

    @Schema(description = "每页数量")
    private Integer pageSize;

    @Schema(description = "筛选后的总数量")
    private Long filteredTotal;

    @Schema(description = "分页数据列表")
    private List<VocabularyImportEntryResponse> items;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
