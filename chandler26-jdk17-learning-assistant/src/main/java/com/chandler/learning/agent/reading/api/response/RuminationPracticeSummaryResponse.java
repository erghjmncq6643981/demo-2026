package com.chandler.learning.agent.reading.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 场景材料句子巩固汇总指标。
 */
@Data
@Schema(name = "反刍场景材料句子巩固汇总响应")
public class RuminationPracticeSummaryResponse {

    @Schema(description = "材料中包含目标词的可练习句子总数")
    private Integer totalTargetSentences;

    @Schema(description = "默认推荐巩固的重点句子数量")
    private Integer recommendedCount;

    @Schema(description = "至少完成一次原句跟敲的句子数量")
    private Integer copyCompletedSentences;

    @Schema(description = "已完成目标词无提示独立遮挡补全的句子数量")
    private Integer clozePassedSentences;

    @Schema(description = "已在当前材料独立拼写结算掌握度的目标词总数")
    private Integer independentlySettledWords;

    @Schema(description = "当前场景材料关联的目标词汇总数")
    private Integer totalTargetWords;
}
