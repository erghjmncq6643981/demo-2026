package com.chandler.learning.agent.vocabulary.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chandler.learning.agent.common.persistence.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 用户个人查词与搜索历史足迹实体。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("learning_vocabulary_lookup_history")
public class LearningVocabularyLookupHistory extends BaseEntity {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    @Schema(description = "主键")
    private Long id;

    @Schema(description = "查询用户 ID")
    private Long userId;

    @Schema(description = "关联公共词卡缓存 ID")
    private Long vocabularyId;

    @Schema(description = "展示单词或短语")
    private String term;

    @Schema(description = "归一化单词或短语")
    private String normalizedTerm;

    @Schema(description = "查询来源：study-卡片查词，quick_lookup-快捷查词，wordbook_add-手动加词")
    private String lookupSource;

    @Schema(description = "累计查询次数")
    private Integer lookupCount;

    @Schema(description = "首次查询时间")
    private LocalDateTime firstLookupTime;

    @Schema(description = "最近查询时间")
    private LocalDateTime lastLookupTime;

    @Schema(description = "最近一次在复习流中过关时间")
    private LocalDateTime lastReviewedTime;
}
