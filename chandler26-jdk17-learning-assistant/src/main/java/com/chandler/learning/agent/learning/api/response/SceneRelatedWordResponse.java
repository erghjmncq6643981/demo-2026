package com.chandler.learning.agent.learning.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Data;

/** 场景相关词展示对象。 */
@Data
public class SceneRelatedWordResponse {
    @Schema(description = "主键标识")
    private Long id;
    @Schema(description = "场景材料标识")
    private Long sceneMaterialId;
    @Schema(description = "英文词汇")
    private String term;
    @Schema(description = "标准化词汇")
    private String normalizedTerm;
    @Schema(description = "音标")
    private String phonetic;
    @Schema(description = "释义")
    private String meaning;
    @Schema(description = "语境释义")
    private String contextMeaning;
    @Schema(description = "编码")
    private String categoryCode;
    @Schema(description = "名称")
    private String categoryName;
    @Schema(description = "是否已提升")
    private Boolean promoted;
    @Schema(description = "关联业务标识")
    private Long promotedEntryId;
}
