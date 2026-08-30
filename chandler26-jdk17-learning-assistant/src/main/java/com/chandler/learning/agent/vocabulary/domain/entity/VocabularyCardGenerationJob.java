package com.chandler.learning.agent.vocabulary.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chandler.learning.agent.common.persistence.BaseEntity;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 批量 AI 词卡生成任务。
 */
@Data
@TableName("vocabulary_card_generation_job")
public class VocabularyCardGenerationJob extends BaseEntity {

    /** 主键 ID。 */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /** 用户 ID。 */
    private Long userId;

    /** 学习计划 ID。 */
    private Long planId;

    /** 学习场景单元 ID。 */
    private Long unitId;

    /** 统一任务中心主键。 */
    private Long asyncTaskId;

    /** 当前业务状态。 */
    private String status;

    /** 单批处理数量。 */
    private Integer batchSize;

    /** 任务或分页数据总数。 */
    private Integer totalCount;

    /** 处理成功数量。 */
    private Integer successCount;

    /** 处理失败数量。 */
    private Integer failedCount;

    /** 错误原因。 */
    private String errorMessage;

    /** 执行开始时间。 */
    private LocalDateTime startedTime;

    /** 执行结束时间。 */
    private LocalDateTime finishedTime;
}
