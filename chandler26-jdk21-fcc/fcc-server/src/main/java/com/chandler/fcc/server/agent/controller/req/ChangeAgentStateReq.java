package com.chandler.fcc.server.agent.controller.req;

import com.chandler.fcc.server.agent.domain.AgentLoginStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
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
    @NotNull(message = "坐席工作状态不能为空")
    @Schema(description = "目标接单状态，仅支持 LOGIN 示闲或 LOGIN_BUSY 示忙", example = "LOGIN_BUSY")
    private AgentLoginStatus status;
}
