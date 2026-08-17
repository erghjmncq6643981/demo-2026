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
    private Long id;

    private Long planId;

    private Integer unitNo;

    private String title;

    private String scenarioType;

    private String summary;

    private String status;

    private Integer coreWordCount;

    private Integer extendedWordCount;

    private Integer supplementaryWordCount;

    private Integer completedCoreCount;

    private Long sceneMaterialId;

    private LocalDate recommendedDate;

    private LocalDateTime generatedTime;

    private LocalDateTime startedTime;

    private LocalDateTime completedTime;
}
