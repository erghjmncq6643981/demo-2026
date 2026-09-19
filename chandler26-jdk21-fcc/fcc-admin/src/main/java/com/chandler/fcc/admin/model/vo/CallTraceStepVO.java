package com.chandler.fcc.admin.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 通话全生命周期时序流水线步骤追踪 VO (Stage + Action)
 *
 * @author Chandler
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "通话时序链路追踪步骤")
public class CallTraceStepVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "相对时间偏移", example = "+00:00.0s")
    private String timeOffset;

    @Schema(description = "流水线大阶段 (TRIGGER, ROUTE, CONNECTED, END)", example = "ROUTE")
    private String stage;

    @Schema(description = "阶段中文名称", example = "路由决策阶段")
    private String stageName;

    @Schema(description = "动作编码", example = "HTTP_CALLBACK")
    private String actionCode;

    @Schema(description = "动作名称", example = "回调司机热线接口")
    private String actionName;

    @Schema(description = "执行详情/出入参说明", example = "POST /api/v1/driver/hotline/match 匹配坐席: 鹏飞(902987)")
    private String detail;

    @Schema(description = "执行状态 (SUCCESS, WARNING, FAILED)", example = "SUCCESS")
    private String status;

    @Schema(description = "耗时描述", example = "140ms")
    private String duration;
}
