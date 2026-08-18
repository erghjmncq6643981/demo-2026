package com.chandler.learning.agent.domain.entity.learning;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chandler.learning.agent.domain.entity.BaseEntity;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 场景学习单元。
 */
@Data
@TableName("learning_plan_unit")
public class LearningPlanUnit extends BaseEntity {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    /** 场景单元主键。 */
    private Long id;

    /** 所属学习计划 ID。 */
    private Long planId;

    /** 计划内单元序号。 */
    private Integer unitNo;

    /** 场景标题。 */
    private String title;

    /** 场景类型。 */
    private String scenarioType;

    /** 场景摘要。 */
    private String summary;

    /** 单元状态。 */
    private String status;

    /** 核心词数量。 */
    private Integer coreWordCount;

    /** 扩展词数量。 */
    private Integer extendedWordCount;

    /** 场景补充词数量。 */
    private Integer supplementaryWordCount;

    /** 已完成检查的核心词数量。 */
    private Integer completedCoreCount;

    /** 场景材料 ID。 */
    private Long sceneMaterialId;

    /** 学习者建议学习日期。 */
    private LocalDate recommendedDate;

    /** AI 材料生成时间。 */
    private LocalDateTime generatedTime;

    /** 开始学习时间。 */
    private LocalDateTime startedTime;

    /** 完成时间。 */
    private LocalDateTime completedTime;
}
