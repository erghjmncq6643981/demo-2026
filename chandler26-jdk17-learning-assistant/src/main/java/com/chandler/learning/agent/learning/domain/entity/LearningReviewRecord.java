package com.chandler.learning.agent.learning.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chandler.learning.agent.common.persistence.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 单次复习记录 DO。
 */
@Data
@TableName("learning_review_record")
public class LearningReviewRecord extends BaseEntity {

    /**
     * 主键。
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    @Schema(description = "主键")
    private Long id;

    /**
     * 用户 ID。
     */
    @Schema(description = "用户 ID")
    private Long userId;

    /**
     * 单词本 ID。
     */
    @Schema(description = "单词本 ID")
    private Long wordbookId;

    /**
     * 单词本词条 ID。
     */
    @Schema(description = "单词本词条 ID")
    private Long entryId;

    /**
     * 公共词汇缓存 ID。
     */
    @Schema(description = "公共词汇缓存 ID")
    private Long vocabularyId;

    /** 用户跨词本逐词进度 ID。 */
    private Long wordProgressId;

    /** 学习计划 ID。 */
    private Long planId;

    /** 学习场景单元 ID。 */
    private Long unitId;

    /** meaning_choice、copy_typing 或 meaning_spelling。 */
    private String assessmentType;

    /** 评测题目 JSON。 */
    private String questionJson;

    /** 学习者提交的答案文本。 */
    private String answerText;

    /** 正确答案。 */
    private String correctAnswer;

    /** 评测检查结果。 */
    private String checkResult;

    /** 跟敲准确率。 */
    private Double typingAccuracy;

    /** 答题提示等级。 */
    private Integer hintLevel;

    /** 已尝试次数。 */
    private Integer attemptCount;

    /** 持续时间，单位毫秒。 */
    private Long durationMillis;

    /**
     * 归一化单词或短语。
     */
    @Schema(description = "归一化单词或短语")
    private String normalizedTerm;

    /**
     * 复习结果：remembered/vague/forgotten。
     */
    @Schema(description = "复习结果：remembered/vague/forgotten")
    private String result;

    /**
     * 本次自评分。
     */
    @Schema(description = "本次自评分")
    private Integer score;

    /**
     * 复习前阶段。
     */
    @Schema(description = "复习前阶段")
    private Integer reviewStageBefore;

    /**
     * 复习后阶段。
     */
    @Schema(description = "复习后阶段")
    private Integer reviewStageAfter;

    /**
     * 复习前掌握度。
     */
    @Schema(description = "复习前掌握度")
    private Integer masteryBefore;

    /**
     * 复习后掌握度。
     */
    @Schema(description = "复习后掌握度")
    private Integer masteryAfter;

    /**
     * 下次复习时间。
     */
    @Schema(description = "下次复习时间")
    private LocalDateTime nextReviewTime;

    /**
     * 本次复习耗时秒数。
     */
    @Schema(description = "本次复习耗时秒数")
    private Integer durationSeconds;
}
