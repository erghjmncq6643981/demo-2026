package com.chandler.fcc.admin.controller;

import com.chandler.fcc.admin.model.CommonResult;
import com.chandler.fcc.admin.model.vo.AgentSipConfigVO;
import com.chandler.fcc.admin.service.AgentSipConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import io.swagger.v3.oas.annotations.Operation;

/** 本人终端注册配置接口，禁用浏览器及代理缓存。 */
@RestController
@RequiredArgsConstructor
public class AgentSipConfigController {
    private final AgentSipConfigService service;

    /** 读取本人 SIP 配置，不接收其他坐席标识。
     * @return 禁止缓存的本人配置
     */
    @GetMapping("/api/admin/auth/sip-config")
    @Operation(summary = "获取本人 WebRTC 注册配置")
    public ResponseEntity<CommonResult<AgentSipConfigVO>> current() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Pragma", "no-cache")
                .body(CommonResult.success(service.current()));
    }
}
