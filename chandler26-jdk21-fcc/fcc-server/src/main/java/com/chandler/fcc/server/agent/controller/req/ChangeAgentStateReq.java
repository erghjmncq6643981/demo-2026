package com.chandler.fcc.server.agent.controller.req;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 坐席工作状态修改请求。
 */
@Getter
@Setter
@NoArgsConstructor
@Schema(description = "坐席工作状态修改请求")
public class ChangeAgentStateReq {

    /**
     * 坐席目标工作状态。
     */
    @Schema(description = "目标工作状态，仅支持 READY 就绪或 REST 休息", example = "READY")
    private String status;
}
