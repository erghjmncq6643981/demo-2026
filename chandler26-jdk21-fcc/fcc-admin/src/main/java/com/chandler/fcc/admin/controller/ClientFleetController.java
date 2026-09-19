package com.chandler.fcc.admin.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.chandler.fcc.admin.model.CommonResult;
import com.chandler.fcc.admin.model.dto.ClientVersionReleaseReq;
import com.chandler.fcc.admin.model.vo.ClientHardwareRecordVO;
import com.chandler.fcc.admin.model.vo.ClientVersionReleaseVO;
import com.chandler.fcc.admin.service.ClientFleetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 坐席客户端版本治理与硬件指纹审计 REST 控制器
 *
 * @author Chandler
 */
@Tag(name = "客户端版本与终端治理", description = "提供PC/Web客户端版本发布、强制升级策略与坐席终端硬件指纹审计接口")
@RestController
@RequestMapping("/api/admin/fleet")
@RequiredArgsConstructor
public class ClientFleetController {

    private final ClientFleetService fleetService;

    /**
     * 发布客户端新版本
     *
     * @param req 版本入参
     * @return 版本 ID
     */
    @Operation(summary = "发布客户端新版本")
    @PostMapping("/versions")
    public CommonResult<Long> publishVersion(@Valid @RequestBody ClientVersionReleaseReq req) {
        Long id = fleetService.publishVersion(req, StpUtil.getLoginIdAsString());
        return CommonResult.success(id);
    }

    /**
     * 查询指定平台的客户端版本列表
     *
     * @param platform 平台 (WINDOWS, MAC, LINUX, WEB)
     * @return 版本列表
     */
    @Operation(summary = "查询客户端版本列表")
    @GetMapping("/versions")
    public CommonResult<List<ClientVersionReleaseVO>> listVersions(
            @RequestParam(value = "platform", required = false) String platform) {
        List<ClientVersionReleaseVO> list = fleetService.listVersions(platform);
        return CommonResult.success(list);
    }

    /**
     * 查询坐席硬件登录安全审计日志
     *
     * @param agentId 坐席 ID (可选)
     * @return 硬件指纹轨迹列表
     */
    @Operation(summary = "查询坐席硬件安全审计轨迹")
    @GetMapping("/hardware")
    public CommonResult<List<ClientHardwareRecordVO>> listHardwareAudit(
            @RequestParam(value = "agentId", required = false) Long agentId) {
        List<ClientHardwareRecordVO> list = fleetService.listHardwareAudit(agentId);
        return CommonResult.success(list);
    }
}
