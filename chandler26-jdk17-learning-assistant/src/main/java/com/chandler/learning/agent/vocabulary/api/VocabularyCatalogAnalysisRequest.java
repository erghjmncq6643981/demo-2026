package com.chandler.learning.agent.vocabulary.api;

import lombok.Data;

/** 公共词本关联分析触发请求。 */
@Data
public class VocabularyCatalogAnalysisRequest {

    /** 立即执行或 low_cost_window。 */
    private String executionMode;

    /** 可选模型配置。 */
    private Long modelConfigId;

    /** 每批词条数，默认 100。 */
    private Integer batchSize;

    /** 是否强制创建新的分析版本。 */
    private Boolean force;
}
