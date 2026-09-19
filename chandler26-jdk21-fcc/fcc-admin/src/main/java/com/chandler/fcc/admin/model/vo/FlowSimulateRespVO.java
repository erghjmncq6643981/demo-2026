package com.chandler.fcc.admin.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 流程仿真模拟运行推演结果视图
 *
 * @author Chandler
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "流程仿真推演结果视图")
public class FlowSimulateRespVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "推演是否完全通过", example = "true")
    private Boolean success;

    @Schema(description = "仿真模拟生成的虚拟会话 ID", example = "SIM-20260918-001")
    private String simulationId;

    @Schema(description = "最终命中路由决策", example = "HTTP_CALLBACK ➔ 坐席: 鹏飞(902987)")
    private String decisionResult;

    @Schema(description = "分配的目标坐席工号", example = "902987")
    private String targetAgentWorkNo;

    @Schema(description = "分配的目标坐席姓名", example = "鹏飞")
    private String targetAgentName;

    @Schema(description = "阶段执行流水线跟踪")
    private List<CallTraceStepVO> traces;
}
