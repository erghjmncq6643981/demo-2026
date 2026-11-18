package com.chandler.learning.agent.vocabulary.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 近期查词轻量复习结果提交请求。
 */
@Data
@Schema(description = "近期查词轻量复习结果提交请求")
public class RecentSearchReviewSubmitRequest {

    @Schema(description = "查词足迹记录 ID（可选）")
    private Long lookupId;

    @Schema(description = "已关联的个人单词本词条 ID（可选，未入词本时为空）")
    private Long entryId;

    @NotBlank(message = "复习词汇不能为空")
    @Schema(description = "复习的英文单词或短语")
    private String term;

    @NotBlank(message = "复习反馈结果不能为空")
    @Schema(description = "复习反馈结果：remembered-认识，vague-模糊，forgotten-没印象")
    private String result;

    @Schema(description = "本次触发的复习方式：quick_pass-认识秒过，guided_copy-模糊跟敲，blind_recall-没印象盲敲")
    private String reviewAction;

    @Schema(description = "本次复习耗时（秒）")
    private Integer durationSeconds;
}
