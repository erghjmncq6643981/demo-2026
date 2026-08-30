package com.chandler.learning.agent.task.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chandler.learning.agent.common.persistence.BaseEntity;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 批处理任务统一记录，供任务中心和后台调度器共同使用。
 */
@Data
@TableName("learning_ai_async_task")
public class AiAsyncTask extends BaseEntity {

    /** 主键 ID。 */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /** 用户 ID。 */
    private Long userId;

    /** 任务成果所属用户 ID。 */
    private Long ownerUserId;

    /** 触发任务的用户 ID，系统触发时为空。 */
    private Long triggerUserId;

    /** 最近一次人工干预用户 ID。 */
    private Long operatorUserId;

    /** 触发来源：user、admin、system。 */
    private String triggerType;

    /** 可见范围：owner_admin、admin。 */
    private String visibility;

    /** AI 异步任务类型。 */
    private String taskType;

    /** AI 异步任务名称。 */
    private String taskName;

    /** 学习计划 ID。 */
    private Long planId;

    /** 学习场景单元 ID。 */
    private Long unitId;

    /** 关联业务任务 ID。 */
    private Long relatedJobId;

    /** 关联业务类型。 */
    private String businessType;

    /** 关联业务数据 ID。 */
    private String businessId;

    /** 业务幂等键。 */
    private String idempotencyKey;

    /** 当前业务状态。 */
    private String status;

    /** 任务执行方式。 */
    private String executionMode;

    /** 计划执行时间。 */
    private LocalDateTime scheduledTime;

    /** 执行优先级。 */
    private Integer priority;

    /** 任务或分页数据总数。 */
    private Integer totalCount;

    /** 处理成功数量。 */
    private Integer successCount;

    /** 处理失败数量。 */
    private Integer failedCount;

    /** 任务进度百分比。 */
    private Integer progressPercent;

    /** 已重试次数。 */
    private Integer retryCount;

    /** 最大重试次数。 */
    private Integer maxRetryCount;

    /** 异步任务载荷 JSON。 */
    private String payloadJson;

    /** 错误原因。 */
    private String errorMessage;

    /** 执行开始时间。 */
    private LocalDateTime startedTime;

    /** 执行结束时间。 */
    private LocalDateTime finishedTime;

    /** 任务取消时间。 */
    private LocalDateTime cancelledTime;
}
