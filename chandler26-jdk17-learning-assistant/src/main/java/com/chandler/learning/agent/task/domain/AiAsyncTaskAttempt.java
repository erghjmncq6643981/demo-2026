package com.chandler.learning.agent.task.domain;

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

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    private Long taskId;

    private Long stepId;

    /** 本次继续或自动执行的操作者用户 ID，系统执行可为空。 */
    private Long operatorUserId;

    private Integer attemptNo;

    private String status;

    private Long modelConfigId;

    private String provider;

    private String modelName;

    private Long modelCallRecordId;

    private Integer promptTokens;

    private Integer completionTokens;

    private Integer totalTokens;

    private Long costTime;

    private String errorCode;

    private String errorMessage;

    private LocalDateTime startedTime;

    private LocalDateTime finishedTime;
}
