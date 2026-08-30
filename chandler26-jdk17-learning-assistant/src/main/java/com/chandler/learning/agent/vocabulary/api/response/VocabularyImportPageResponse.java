package com.chandler.learning.agent.vocabulary.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Data;

import java.util.List;

/**
 * 管理员公共词本导入历史分页结果。
 */
@Data
public class VocabularyImportPageResponse {

    /** 符合筛选条件的导入任务总数。 */
    @Schema(description = "分页数据总数")
    private Long total;

    /** 当前页码，从 1 开始。 */
    @Schema(description = "页码")
    private Integer page;

    /** 每页任务数。 */
    @Schema(description = "每页数量")
    private Integer pageSize;

    /** 当前页的轻量导入任务摘要。 */
    @Schema(description = "分页数据列表")
    private List<VocabularyImportResponse> items;
}
