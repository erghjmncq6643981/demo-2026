package com.chandler.learning.agent.learning.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chandler.learning.agent.common.persistence.BaseEntity;
import lombok.Data;

import java.time.LocalDate;

/** 学习活动按用户、日期和指标聚合的读模型 DO。 */
@Data
@TableName("learning_activity_daily")
public class LearningActivityDaily extends BaseEntity {

    /** 汇总行主键。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 用户 ID。 */
    private Long userId;

    /** 活动日期。 */
    private LocalDate activityDate;

    /** 指标编码，与活动事件类型编码一致。 */
    private String metricType;

    /** 指标数量。 */
    private Integer metricCount;

    /** 累计有效时长，单位秒。 */
    private Integer durationSeconds;

    /** 指标中的正确数量。 */
    private Integer correctCount;

    /** 指标总尝试数量。 */
    private Integer totalCount;
}
