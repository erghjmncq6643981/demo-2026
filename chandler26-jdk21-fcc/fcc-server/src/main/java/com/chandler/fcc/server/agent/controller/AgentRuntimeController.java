package com.chandler.fcc.server.agent.controller;

import com.chandler.fcc.server.agent.application.AgentRuntimeService;
import com.chandler.fcc.server.agent.controller.req.ChangeAgentStateReq;
import com.chandler.fcc.server.agent.controller.resp.AgentRuntimeResp;
import com.chandler.fcc.server.agent.controller.resp.AgentRuntimeStateResp;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提供当前坐席工作状态的查询与修改接口。
 */
@RestController
@RequestMapping("/api/telephony/agent-state")
@RequiredArgsConstructor
public class AgentRuntimeController {

    private final AgentRuntimeService service;

    /**
     * 查询当前登录坐席的权威工作状态。
     *
     * @return 状态响应
     */
    @GetMapping
    public AgentRuntimeResp state() {
        return AgentRuntimeResp.success(AgentRuntimeStateResp.from(service.status()));
    }

    /**
     * 修改当前登录坐席的非通话状态。
     *
     * @param request 状态修改请求
     * @return 数据库中的最终状态
     */
    @PostMapping
    public AgentRuntimeResp change(@Valid @RequestBody ChangeAgentStateReq request) {
        return AgentRuntimeResp.success(
            AgentRuntimeStateResp.from(service.change(request.getStatus()))
        );
    }
}
