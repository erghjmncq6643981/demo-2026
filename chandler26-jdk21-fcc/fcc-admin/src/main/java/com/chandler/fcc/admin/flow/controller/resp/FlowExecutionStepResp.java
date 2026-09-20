package com.chandler.fcc.admin.flow.controller.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

/**
 * 一次真实流程阶段尝试及其指令、事件和结果。
 */
@Getter
@Builder
@Schema(description = "通话流程阶段执行事实")
public class FlowExecutionStepResp {

    @Schema(description = "执行事实标识，按不透明字符串传输")
    private String id;

    @Schema(description = "固定模型阶段键", example = "MENU")
    private String stepKey;

    @Schema(description = "公共流程动作代码", example = "READ_DTMF")
    private String actionType;

    @Schema(description = "同一阶段的尝试次数", example = "1")
    private Integer attemptNo;

    @Schema(description = "本次尝试状态", example = "SUCCEEDED")
    private String status;

    @Schema(description = "关联的稳定命令标识，无指令时为空")
    private String commandId;

    @Schema(description = "驱动阶段转移的事件标识，内部动作时为空")
    private String eventId;

    @Schema(description = "阶段输入边界 JSON")
    private String input;

    @Schema(description = "阶段输出或决策结果 JSON")
    private String output;

    @Schema(description = "失败错误码，非失败状态为空")
    private String errorCode;

    @Schema(description = "尝试开始时间")
    private LocalDateTime startedAt;

    @Schema(description = "尝试结束时间，等待事件时为空")
    private LocalDateTime endedAt;

    @Schema(description = "尝试耗时毫秒数")
    private Long durationMs;
}
