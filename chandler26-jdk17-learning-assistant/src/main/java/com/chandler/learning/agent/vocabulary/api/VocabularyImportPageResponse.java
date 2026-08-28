package com.chandler.learning.agent.vocabulary.api;

import lombok.Data;

import java.util.List;

/**
 * 管理员公共词本导入历史分页结果。
 */
@Data
public class VocabularyImportPageResponse {

    /** 符合筛选条件的导入任务总数。 */
    private Long total;

    /** 当前页码，从 1 开始。 */
    private Integer page;

    /** 每页任务数。 */
    private Integer pageSize;

    /** 当前页的轻量导入任务摘要。 */
    private List<VocabularyImportResponse> items;
}
