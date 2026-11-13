package com.chandler.learning.agent.reading.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 知识反刍阅读后按句跟敲与目标词遮挡补全提交请求。
 */
@Data
@Schema(name = "知识反刍句子巩固提交请求")
public class RuminationSentencePracticeSubmitRequest {

    @NotNull(message = "场景材料 ID 不能为空")
    @Schema(description = "关联场景材料文章 ID")
    private Long materialId;

    @NotNull(message = "单词本 ID 不能为空")
    @Schema(description = "当前个人单词本 ID")
    private Long wordbookId;

    @NotNull(message = "句子序号不能为空")
    @Min(value = 0, message = "句子序号不能为负数")
    @Schema(description = "场景材料正文中的句子序号（从 0 开始）")
    private Integer sentenceIndex;

    @NotBlank(message = "目标词汇不能为空")
    @Schema(description = "本句重点巩固的目标词汇原词")
    private String targetWord;

    @NotBlank(message = "巩固模式不能为空")
    @Schema(description = "巩固练习模式：copy_sentence（原句跟敲）或 cloze_recall（目标词遮挡补全）")
    private String practiceMode;

    @NotBlank(message = "作答内容不能为空")
    @Schema(description = "学习者输入的英文句子或目标词拼写")
    private String userAnswer;

    @Schema(description = "原句跟敲阶段累计错键次数（可选）")
    private Integer wrongKeyCount;

    @Schema(description = "遮挡补全阶段是否使用过查看原句或偷看提示")
    private Boolean usedHint;

    @Schema(description = "遮挡补全阶段当前题次尝试次数（从 1 开始）")
    private Integer attemptCount;

    @Schema(description = "本次单句巩固耗时（毫秒）")
    private Long durationMillis;
}
