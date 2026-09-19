package com.chandler.fcc.admin.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 流程仿真模拟运行推演入参
 *
 * @author Chandler
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "流程仿真推演入参")
public class FlowSimulateReq implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "流程唯一标识", example = "FLOW-INBOUND")
    private String flowKey;

    @Schema(description = "测试主叫手机号", example = "13483983247")
    private String caller;

    @Schema(description = "进线 DID 号码", example = "021-50881001")
    private String did;

    @Schema(description = "客户按键 DTMF 输入", example = "2")
    private String dtmf;

    @Schema(description = "路由模式 (DID_DIRECT, RULE_ENGINE, HTTP_CALLBACK)", example = "HTTP_CALLBACK")
    private String routeMode;
}
