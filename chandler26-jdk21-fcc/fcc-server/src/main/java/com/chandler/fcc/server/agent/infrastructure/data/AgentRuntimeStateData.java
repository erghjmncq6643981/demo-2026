package com.chandler.fcc.server.agent.infrastructure.data;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 坐席运行状态持久化查询结果。
 */
@Getter
@Setter
@NoArgsConstructor
public class AgentRuntimeStateData {

    private String loginStatus;
    private String workStatus;
    private String activeCallId;
}
