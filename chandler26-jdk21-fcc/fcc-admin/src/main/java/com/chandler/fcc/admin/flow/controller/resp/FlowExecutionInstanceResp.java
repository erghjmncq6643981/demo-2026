package com.chandler.fcc.admin.flow.controller.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

/**
 * 单通话固定的流程版本快照和实例状态。
 */
@Getter
@Builder
@Schema(description = "通话流程实例摘要")
public class FlowExecutionInstanceResp {

    @Schema(description = "流程实例标识，按不透明字符串传输")
    private String id;

    @Schema(description = "FCC 业务通话标识")
    private String callId;

    @Schema(description = "通话启动时固定的流程版本标识")
    private String versionId;

    @Schema(description = "流程实例状态", example = "RUNNING")
    private String status;

    @Schema(description = "当前阶段键", example = "ROUTE")
    private String currentStep;

    @Schema(description = "通话启动时固定的完整模型快照 JSON")
    private String snapshot;

    @Schema(description = "流程开始时间")
    private LocalDateTime startedAt;

    @Schema(description = "流程结束时间，运行中为空")
    private LocalDateTime endedAt;
}
