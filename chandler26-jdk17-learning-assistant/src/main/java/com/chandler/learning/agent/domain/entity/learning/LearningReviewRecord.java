package com.chandler.learning.agent.domain.entity.learning;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chandler.learning.agent.domain.entity.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 单次复习记录 DO。
 */
@Data
@TableName("learning_review_record")
public class LearningReviewRecord extends BaseEntity {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    @Schema(description = "主键")
    private Long id;

    @Schema(description = "用户 ID")
    private Long userId;

    @Schema(description = "单词本 ID")
    private Long wordbookId;

    @Schema(description = "单词本词条 ID")
    private Long entryId;

    @Schema(description = "公共词汇缓存 ID")
    private Long vocabularyId;

    @Schema(description = "归一化单词或短语")
    private String normalizedTerm;

    @Schema(description = "复习结果：remembered/vague/forgotten")
    private String result;

    @Schema(description = "本次自评分")
    private Integer score;

    @Schema(description = "复习前阶段")
    private Integer reviewStageBefore;

    @Schema(description = "复习后阶段")
    private Integer reviewStageAfter;

    @Schema(description = "复习前掌握度")
    private Integer masteryBefore;

    @Schema(description = "复习后掌握度")
    private Integer masteryAfter;

    @Schema(description = "下次复习时间")
    private LocalDateTime nextReviewTime;

    @Schema(description = "本次复习耗时秒数")
    private Integer durationSeconds;
}
