package com.chandler.learning.agent.domain.entity.learning;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chandler.learning.agent.domain.entity.BaseEntity;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 批处理任务统一记录，供任务中心和后台调度器共同使用。
 */
@Data
@TableName("learning_ai_async_task")
public class AiAsyncTask extends BaseEntity {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private String taskType;

    private String taskName;

    private Long planId;

    private Long unitId;

    private Long relatedJobId;

    private String status;

    private String executionMode;

    private LocalDateTime scheduledTime;

    private Integer priority;

    private Integer totalCount;

    private Integer successCount;

    private Integer failedCount;

    private Integer progressPercent;

    private Integer retryCount;

    private Integer maxRetryCount;

    private String payloadJson;

    private String errorMessage;

    private LocalDateTime startedTime;

    private LocalDateTime finishedTime;

    private LocalDateTime cancelledTime;
}
