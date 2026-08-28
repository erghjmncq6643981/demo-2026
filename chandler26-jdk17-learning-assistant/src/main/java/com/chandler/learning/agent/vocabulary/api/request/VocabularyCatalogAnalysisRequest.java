package com.chandler.learning.agent.vocabulary.api.request;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Data;

/** 公共词本关联分析触发请求。 */
@Data
public class VocabularyCatalogAnalysisRequest {

    /** 立即执行或 low_cost_window。 */
    @Schema(description = "执行方式")
    private String executionMode;

    /** 可选模型配置。 */
    @Schema(description = "模型配置标识")
    private Long modelConfigId;

    /** 每批词条数，默认 100。 */
    @Schema(description = "批处理数量")
    private Integer batchSize;

    /** 是否强制创建新的分析版本。 */
    @Schema(description = "是否强制执行")
    private Boolean force;
}
