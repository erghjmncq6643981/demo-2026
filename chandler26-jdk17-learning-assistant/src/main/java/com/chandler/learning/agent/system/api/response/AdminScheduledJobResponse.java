package com.chandler.learning.agent.system.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 后台定时任务管理响应数据。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminScheduledJobResponse {

    @Schema(description = "任务唯一标识，例如 audio_sync")
    private String jobKey;

    @Schema(description = "任务中文名称")
    private String name;

    @Schema(description = "任务功能描述")
    private String description;

    @Schema(description = "调度规则或表达式，例如 0 0 3 * * ?")
    private String cronExpression;

    @Schema(description = "当前是否正在运行中")
    private Boolean running;

    @Schema(description = "最近一次执行触发时间")
    private LocalDateTime lastRunTime;

    @Schema(description = "最近一次执行耗时（毫秒）")
    private Long lastCostMs;

    @Schema(description = "最近一次执行状态，例如 IDLE, RUNNING, SUCCESS, FAILED")
    private String lastStatus;

    @Schema(description = "最近一次执行结果摘要")
    private String lastSummary;
}
