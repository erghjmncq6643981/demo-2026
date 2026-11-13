package com.chandler.learning.agent.reading.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 句子中匹配到的目标词跨度及巩固状态。
 */
@Data
@Schema(name = "句子目标词跨度响应")
public class RuminationSentenceTargetSpanResponse {

    @Schema(description = "归一化目标词汇原词")
    private String targetWord;

    @Schema(description = "句子语境中目标词实际展示词形")
    private String surfaceForm;

    @Schema(description = "目标词在英文句子中的起始字符位置（包含）")
    private Integer startOffset;

    @Schema(description = "目标词在英文句子中的结束字符位置（不包含）")
    private Integer endOffset;

    @Schema(description = "该句子与目标词的原句跟敲完成次数")
    private Integer copyCount;

    @Schema(description = "该目标词在此句遮挡补全的累计提交次数")
    private Integer clozeAttemptCount;

    @Schema(description = "该目标词是否已在此句无提示独立补全通过")
    private Boolean clozeIndependentPassed;
}
