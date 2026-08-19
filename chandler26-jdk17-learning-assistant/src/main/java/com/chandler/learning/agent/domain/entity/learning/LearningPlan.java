package com.chandler.learning.agent.domain.entity.learning;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chandler.learning.agent.domain.entity.BaseEntity;
import lombok.Data;

/**
 * 基于一个已发布词表版本的场景化自助学习计划。
 */
@Data
@TableName("learning_plan")
public class LearningPlan extends BaseEntity {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    /** 学习计划主键。 */
    private Long id;

    /** 计划所属用户 ID。 */
    private Long userId;

    /** 使用的公共词本 ID。 */
    private Long catalogId;

    /** 使用的公共词本版本 ID。 */
    private Long catalogVersionId;

    /** 学习者个人词本 ID。 */
    private Long wordbookId;

    /** 学习计划名称。 */
    private String name;

    /** 用于指导 AI 生成材料的学习目的。 */
    private String learningPurpose;

    /** 计划开始时间。 */
    private java.time.LocalDateTime startTime;

    /** 计划结束时间。 */
    private java.time.LocalDateTime endTime;

    /** 计划状态。 */
    private String status;

    /** 词表总词数。 */
    private Integer totalCatalogWords;

    /** 已完成首次学习的核心词数量。 */
    private Integer learnedCoreWords;

    /** 已完成的场景单元数量。 */
    private Integer completedUnitCount;

    /** 当前正在学习的场景单元 ID。 */
    private Long currentUnitId;

    /** 计划复用的 AI 审计会话 ID；固定生成动作不会把该会话的历史消息发送给模型。 */
    private Long aiSessionId;

    /** 当前场景材料生成租约令牌，用于跨实例防重。 */
    private String generationLockToken;

    /** 当前场景材料生成租约到期时间。 */
    private java.time.LocalDateTime generationLockUntil;
}
