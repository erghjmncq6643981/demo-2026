package com.chandler.fcc.server.agent.controller;

import com.chandler.fcc.server.agent.application.AgentRuntimeService;
import com.chandler.fcc.server.agent.controller.req.ChangeAgentStateReq;
import java.util.Map;
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
    public Map<String, Object> state() {
        return Map.of("code", 200, "data", service.status());
    }

    /**
     * 修改当前登录坐席的非通话状态。
     *
     * @param request 状态修改请求
     * @return 数据库中的最终状态
     */
    @PostMapping
    public Map<String, Object> change(@RequestBody ChangeAgentStateReq request) {
        return Map.of("code", 200, "data", service.change(request.getStatus()));
    }
}
