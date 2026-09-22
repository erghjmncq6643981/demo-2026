package com.chandler.fcc.admin.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.chandler.fcc.admin.agent.application.AgentEndpointService;
import com.chandler.fcc.admin.controller.req.SwitchOwnEndpointReq;
import com.chandler.fcc.admin.controller.resp.AgentEndpointsResp;
import com.chandler.fcc.admin.model.CommonResult;
import com.chandler.fcc.admin.model.vo.AgentSipConfigVO;
import com.chandler.fcc.admin.service.AgentSipConfigService;
import com.chandler.fcc.admin.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import io.swagger.v3.oas.annotations.Operation;

/** 本人终端注册配置接口，禁用浏览器及代理缓存。 */
@RestController
@RequiredArgsConstructor
public class AgentSipConfigController {
    private final AgentSipConfigService sipConfigService;
    private final AgentEndpointService endpointService;

    /** 读取本人 SIP 配置，不接收其他坐席标识。
     * @return 禁止缓存的本人配置
     */
    @GetMapping("/api/admin/auth/sip-config")
    @Operation(summary = "获取本人 WebRTC 注册配置")
    public ResponseEntity<CommonResult<AgentSipConfigVO>> current() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Pragma", "no-cache")
                .body(CommonResult.success(sipConfigService.current()));
    }

    /**
     * 查询当前登录坐席本人的终端绑定和当前选择。
     *
     * @return 本人终端配置
     */
    @GetMapping("/api/admin/auth/endpoints")
    @Operation(summary = "获取本人接听终端配置")
    public CommonResult<AgentEndpointsResp> endpoints() {
        String workNo = requireAgentWorkNo();
        return CommonResult.success(AgentEndpointsResp.from(endpointService.get(workNo)));
    }

    /**
     * 切换当前登录坐席本人的接听终端。
     *
     * @param req 切换请求
     * @return 切换后的本人终端配置
     */
    @PutMapping("/api/admin/auth/endpoint")
    @Operation(summary = "切换本人接听终端")
    public CommonResult<AgentEndpointsResp> switchEndpoint(
        @Valid @RequestBody SwitchOwnEndpointReq req
    ) {
        String workNo = requireAgentWorkNo();
        return CommonResult.success(
            AgentEndpointsResp.from(
                endpointService.switchEndpoint(
                    workNo,
                    req.getEndpointType(),
                    req.getEndpointValue(),
                    workNo
                )
            )
        );
    }

    /**
     * 返回当前经过认证的坐席工号。
     *
     * @return 坐席工号
     * @throws IllegalArgumentException 当前主体不是坐席
     */
    private String requireAgentWorkNo() {
        String accountType = StpUtil.getSession().getString("accountType");
        if (!AuthService.SUBJECT_AGENT.equals(accountType)) {
            throw new IllegalArgumentException("仅坐席本人可以操作本人终端");
        }
        return StpUtil.getLoginIdAsString();
    }
}
