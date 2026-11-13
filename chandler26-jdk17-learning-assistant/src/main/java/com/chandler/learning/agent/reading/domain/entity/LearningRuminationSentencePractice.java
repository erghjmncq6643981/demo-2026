package com.chandler.learning.agent.reading.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chandler.learning.agent.common.persistence.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 知识反刍阅读后按句跟敲与目标词遮挡补全巩固记录实体。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("learning_rumination_sentence_practice")
@Schema(name = "知识反刍句子巩固记录")
public class LearningRuminationSentencePractice extends BaseEntity {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    @Schema(description = "主键")
    private Long id;

    @Schema(description = "所属用户 ID")
    private Long userId;

    @Schema(description = "所属个人单词本 ID")
    private Long wordbookId;

    @Schema(description = "关联场景材料文章 ID")
    private Long materialId;

    @Schema(description = "场景材料正文中的句子序号（从 0 开始）")
    private Integer sentenceIndex;

    @Schema(description = "归一化目标词汇原词")
    private String targetWord;

    @Schema(description = "句子语境中目标词实际展示词形")
    private String surfaceForm;

    @Schema(description = "对应英文句子原文快照")
    private String sentenceText;

    @Schema(description = "原句跟敲完成次数（仅计入练习次数）")
    private Integer copyCount;

    @Schema(description = "最近一次原句跟敲准确率（0-100）")
    private Double lastCopyAccuracy;

    @Schema(description = "目标词遮挡补全累计提交次数")
    private Integer clozeAttemptCount;

    @Schema(description = "借助查看原句或多次试错后通过的次数")
    private Integer clozeAssistedCount;

    @Schema(description = "是否已首次无提示独立补全通过")
    private Boolean clozeIndependentPassed;

    @Schema(description = "该材料下目标词是否已触发拼写掌握度结算")
    private Boolean masterySettled;

    @Schema(description = "最近一次句子练习时间")
    private LocalDateTime lastPracticedTime;
}
