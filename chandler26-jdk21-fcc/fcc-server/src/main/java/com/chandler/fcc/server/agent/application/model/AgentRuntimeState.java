package com.chandler.fcc.server.agent.application.model;

import com.chandler.fcc.server.agent.domain.AgentLoginStatus;
import com.chandler.fcc.server.agent.domain.AgentWorkStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 坐席登录状态与有效工作状态。
 */
@Getter
@Builder
@AllArgsConstructor
public class AgentRuntimeState {

    private final AgentLoginStatus loginStatus;
    private final AgentWorkStatus workStatus;
    private final String activeCallId;
}
