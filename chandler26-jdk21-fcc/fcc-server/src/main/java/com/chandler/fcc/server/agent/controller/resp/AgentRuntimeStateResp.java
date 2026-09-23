package com.chandler.fcc.server.agent.controller.resp;

import com.chandler.fcc.server.agent.application.model.AgentRuntimeState;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 坐席登录状态与工作状态响应。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "坐席登录状态与工作状态")
public class AgentRuntimeStateResp {

    @Schema(description = "坐席业务登录状态", example = "LOGIN")
    private String loginStatus;

    @Schema(description = "有效工作状态；BUSY 表示示忙基态，通话态为 CALLING/RINGING/ANSWERED", example = "READY")
    private String workStatus;

    @Schema(description = "当前通话标识；空闲或话后整理时为空", example = "1950123456789012345")
    private String activeCallId;

    /**
     * 转换应用层状态。
     *
     * @param state 应用层状态
     * @return 接口响应
     */
    public static AgentRuntimeStateResp from(AgentRuntimeState state) {
        return AgentRuntimeStateResp.builder()
            .loginStatus(state.getLoginStatus().name())
            .workStatus(state.getWorkStatus().name())
            .activeCallId(state.getActiveCallId())
            .build();
    }
}
