package com.chandler.learning.agent.task.api.request;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 场景材料预约生成请求。
 */
@Data
public class AiAsyncTaskScheduleRequest {

    @Schema(description = "关联业务标识")
    private Long modelConfigId;

    @Schema(description = "建议学习日期")
    private LocalDate recommendedDate;

    @Schema(description = "执行方式")
    private String executionMode;

    @Schema(description = "计划执行时间")
    private LocalDateTime scheduledTime;

    @Schema(description = "优先级")
    private Integer priority;
}
