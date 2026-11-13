package com.chandler.learning.agent.reading.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 场景材料分句后的可练习句子单元。
 */
@Data
@Schema(name = "反刍场景材料分句单元响应")
public class RuminationPracticeSentenceResponse {

    @Schema(description = "句子在材料正文中的序号（从 0 开始）")
    private Integer sentenceIndex;

    @Schema(description = "句子所属自然段序号（从 0 开始）")
    private Integer paragraphIndex;

    @Schema(description = "分句后的英文原句文本")
    private String sentenceText;

    @Schema(description = "本句包含的目标词跨度与练习进度列表")
    private List<RuminationSentenceTargetSpanResponse> targetSpans;

    @Schema(description = "默认重点目标词汇原词")
    private String primaryTargetWord;

    @Schema(description = "是否推荐纳入默认重点巩固句列表")
    private Boolean recommended;

    @Schema(description = "推荐巩固优先级分值（越大越优先）")
    private Integer recommendPriority;

    @Schema(description = "该句累计完成原句跟敲次数")
    private Integer copyCount;

    @Schema(description = "该句主要目标词是否已无提示独立通过遮挡补全")
    private Boolean clozeIndependentPassed;
}
