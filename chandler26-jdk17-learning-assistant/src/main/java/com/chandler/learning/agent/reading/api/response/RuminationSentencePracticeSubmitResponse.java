package com.chandler.learning.agent.reading.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识反刍句子巩固提交响应。
 */
@Data
@Schema(name = "知识反刍句子巩固提交响应")
public class RuminationSentencePracticeSubmitResponse {

    @Schema(description = "场景材料文章 ID（作为字符串传输）")
    private String materialId;

    @Schema(description = "句子序号")
    private Integer sentenceIndex;

    @Schema(description = "目标词汇原词")
    private String targetWord;

    @Schema(description = "句子语境中目标词实际展示词形")
    private String surfaceForm;

    @Schema(description = "本次巩固模式编码：copy_sentence 或 cloze_recall")
    private String practiceMode;

    @Schema(description = "本次巩固模式中文名称")
    private String practiceModeLabel;

    @Schema(description = "服务端独立判定的本次输入是否正确")
    private Boolean isCorrect;

    @Schema(description = "服务端核验的标准上下文词形（用于错位定位提示）")
    private String expectedSurfaceForm;

    @Schema(description = "累计完成的原句跟敲次数")
    private Integer copyCount;

    @Schema(description = "本次跟敲或拼写准确率（0-100）")
    private Double accuracy;

    @Schema(description = "累计目标词遮挡补全提交次数")
    private Integer clozeAttemptCount;

    @Schema(description = "当前句子与目标词是否已首次无提示独立补全通过")
    private Boolean clozeIndependentPassed;

    @Schema(description = "本次提交是否使用了查看原句或偷看提示")
    private Boolean usedHint;

    @Schema(description = "本次提交是否首次触发拼写掌握度结算")
    private Boolean masterySettled;

    @Schema(description = "本次结算是否推进了艾宾浩斯下次复习时间")
    private Boolean scheduleAdvanced;

    @Schema(description = "更新后的跨词本拼写掌握度（0-100）")
    private Integer spellingScoreAfter;

    @Schema(description = "更新后的词条整体掌握度（0-100）")
    private Integer masteryScoreAfter;

    @Schema(description = "更新后的词条艾宾浩斯复习阶段")
    private Integer reviewStageAfter;

    @Schema(description = "当前词条下一次复习时间")
    private LocalDateTime nextReviewTime;

    @Schema(description = "面向学习者的分流结算提示文案")
    private String feedbackMessage;

    @Schema(description = "当前场景材料最新的句子巩固汇总统计")
    private RuminationPracticeSummaryResponse practiceSummary;
}
