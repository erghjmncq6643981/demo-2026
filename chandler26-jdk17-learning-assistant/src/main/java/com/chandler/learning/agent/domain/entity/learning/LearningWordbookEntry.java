package com.chandler.learning.agent.domain.entity.learning;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("learning_wordbook_entry")
public class LearningWordbookEntry {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private Long wordbookId;

    private Long vocabularyId;

    private String term;

    private String normalizedTerm;

    private String note;

    /**
     * 加入词书时冻结的个人学习卡快照，避免公共 AI 结果刷新影响已有词条详情。
     */
    private String snapshotRawContent;

    private String snapshotParsedJson;

    private String snapshotTagsJson;

    private String snapshotRelationsJson;

    private String snapshotProvider;

    private String snapshotModelName;

    private Long snapshotSessionId;

    private LocalDateTime snapshotTime;

    private String status;

    private Integer reviewStage;

    private Integer masteryScore;

    private LocalDateTime firstReviewTime;

    private LocalDateTime lastReviewTime;

    private LocalDateTime nextReviewTime;

    private Integer dueCount;

    private Integer reviewCount;

    private Integer correctCount;

    private Integer wrongCount;

    private Boolean deleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
