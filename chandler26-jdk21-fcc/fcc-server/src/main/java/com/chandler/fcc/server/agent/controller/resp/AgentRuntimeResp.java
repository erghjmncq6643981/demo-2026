package com.chandler.fcc.server.agent.controller.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 坐席运行状态接口信封。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "坐席运行状态接口响应")
public class AgentRuntimeResp {

    @Schema(description = "业务状态码", example = "200")
    private Integer code;

    @Schema(description = "坐席运行状态")
    private AgentRuntimeStateResp data;

    /**
     * 构造成功响应。
     *
     * @param data 坐席运行状态
     * @return 成功响应
     */
    public static AgentRuntimeResp success(AgentRuntimeStateResp data) {
        return new AgentRuntimeResp(200, data);
    }
}
