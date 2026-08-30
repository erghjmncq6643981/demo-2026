package com.chandler.learning.agent.task.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chandler.learning.agent.common.persistence.BaseEntity;
import lombok.Data;

import java.time.LocalDateTime;

/** AI 任务步骤的一次执行尝试，用于成本、异常和人工操作审计。 */
@Data
@TableName("learning_ai_async_task_attempt")
public class AiAsyncTaskAttempt extends BaseEntity {

    /** 主键 ID。 */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /** AI 异步任务 ID。 */
    private Long taskId;

    /** 任务步骤 ID。 */
    private Long stepId;

    /** 本次继续或自动执行的操作者用户 ID，系统执行可为空。 */
    private Long operatorUserId;

    /** 当前尝试序号。 */
    private Integer attemptNo;

    /** 当前业务状态。 */
    private String status;

    /** 模型配置 ID。 */
    private Long modelConfigId;

    /** 模型供应商。 */
    private String provider;

    /** 模型名称。 */
    private String modelName;

    /** 模型调用审计记录 ID。 */
    private Long modelCallRecordId;

    /** 模型输入 Token 数。 */
    private Integer promptTokens;

    /** 模型输出 Token 数。 */
    private Integer completionTokens;

    /** 模型调用 Token 总数。 */
    private Integer totalTokens;

    /** 处理耗时，单位毫秒。 */
    private Long costTime;

    /** 错误码。 */
    private String errorCode;

    /** 错误原因。 */
    private String errorMessage;

    /** 执行开始时间。 */
    private LocalDateTime startedTime;

    /** 执行结束时间。 */
    private LocalDateTime finishedTime;
}
