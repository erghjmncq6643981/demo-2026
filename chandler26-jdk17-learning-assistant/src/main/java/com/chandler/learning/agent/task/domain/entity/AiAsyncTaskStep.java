package com.chandler.learning.agent.task.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chandler.learning.agent.common.persistence.BaseEntity;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 可独立恢复的 AI 任务步骤，检查点只保存继续执行所需的受控业务数据。
 */
@Data
@TableName("learning_ai_async_task_step")
public class AiAsyncTaskStep extends BaseEntity {

    /** 主键 ID。 */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /** 父任务 ID。 */
    private Long taskId;

    /** 任务内稳定步骤编码。 */
    private String stepCode;

    /** 面向用户的步骤名称。 */
    private String stepName;

    /** 执行顺序。 */
    private Integer stepOrder;

    /** 步骤状态。 */
    private String status;

    /** 完成数量，适用于批次步骤。 */
    private Integer completedCount;

    /** 总数量，适用于批次步骤。 */
    private Integer totalCount;

    /** 断点续跑检查点 JSON，不保存密钥或无界 AI 原文。 */
    private String checkpointJson;

    /** 当前执行租约令牌。 */
    private String leaseToken;

    /** 当前执行租约到期时间。 */
    private LocalDateTime leaseUntil;

    /** 最近心跳时间。 */
    private LocalDateTime heartbeatTime;

    /** 已执行次数。 */
    private Integer attemptCount;

    /** 最大执行次数。 */
    private Integer maxAttemptCount;

    /** 最近一次失败的可读摘要。 */
    private String errorMessage;

    /** 执行开始时间。 */
    private LocalDateTime startedTime;

    /** 执行结束时间。 */
    private LocalDateTime finishedTime;
}
