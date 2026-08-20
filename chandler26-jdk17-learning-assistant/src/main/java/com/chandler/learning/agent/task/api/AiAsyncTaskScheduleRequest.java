package com.chandler.learning.agent.task.api;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 场景材料预约生成请求。
 */
@Data
public class AiAsyncTaskScheduleRequest {

    private Long modelConfigId;

    private LocalDate recommendedDate;

    private String executionMode;

    private LocalDateTime scheduledTime;

    private Integer priority;
}
