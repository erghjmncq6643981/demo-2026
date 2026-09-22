package com.chandler.fcc.admin.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.chandler.fcc.admin.controller.req.BindDidFlowReq;
import com.chandler.fcc.admin.model.CommonResult;
import com.chandler.fcc.admin.model.dto.DidNumberCreateReq;
import com.chandler.fcc.admin.model.dto.OutboundNumberCreateReq;
import com.chandler.fcc.admin.model.vo.DidNumberVO;
import com.chandler.fcc.admin.model.vo.OutboundNumberVO;
import com.chandler.fcc.admin.model.vo.TelephonyNodeVO;
import com.chandler.fcc.admin.service.TelephonyResourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 通信资源与集群节点监控 REST 控制器
 *
 * @author Chandler
 */
@Tag(name = "通信资源管理", description = "提供DID呼入引示号、外呼主叫池及FreeSWITCH集群节点监控接口")
@RestController
@RequestMapping("/api/admin/resources")
@RequiredArgsConstructor
public class TelephonyResourceController {

    private final TelephonyResourceService resourceService;

    /**
     * 录入呼入 DID 号码
     *
     * @param req DID 号码参数
     * @return DID ID
     */
    @Operation(summary = "录入呼入DID接入引示号")
    @PostMapping("/dids")
    public CommonResult<Long> createDidNumber(@Valid @RequestBody DidNumberCreateReq req) {
        Long id = resourceService.createDidNumber(req);
        return CommonResult.success(id);
    }

    /**
     * 查询全量 DID 号码
     *
     * @return DID 列表
     */
    @Operation(summary = "查询DID接入号列表")
    @GetMapping("/dids")
    public CommonResult<List<DidNumberVO>> listDidNumbers() {
        List<DidNumberVO> list = resourceService.listDidNumbers();
        return CommonResult.success(list);
    }

    /**
     * 将 DID 被叫号码绑定到一个呼入流程。
     *
     * @param id DID 主键 ID
     * @param req 流程绑定参数
     * @return 操作成功响应
     */
    @Operation(summary = "绑定DID被叫号码与IVR流程")
    @PutMapping("/dids/{id}/flow-binding")
    public CommonResult<Void> bindDidFlow(
            @PathVariable("id") Long id,
            @Valid @RequestBody BindDidFlowReq req) {
        StpUtil.checkPermission("resource:write");
        resourceService.bindDidFlow(id, req.getFlowKey());
        return CommonResult.success();
    }

    /**
     * 解除 DID 被叫号码与呼入流程的绑定。
     *
     * @param id DID 主键 ID
     * @return 操作成功响应
     */
    @Operation(summary = "解除DID被叫号码与IVR流程绑定")
    @DeleteMapping("/dids/{id}/flow-binding")
    public CommonResult<Void> unbindDidFlow(@PathVariable("id") Long id) {
        StpUtil.checkPermission("resource:write");
        resourceService.unbindDidFlow(id);
        return CommonResult.success();
    }

    /**
     * 删除 DID 号码
     *
     * @param id DID ID
     * @return 操作成功响应
     */
    @Operation(summary = "删除DID引示号")
    @DeleteMapping("/dids/{id}")
    public CommonResult<Void> deleteDidNumber(@PathVariable("id") Long id) {
        resourceService.deleteDidNumber(id);
        return CommonResult.success();
    }

    /**
     * 录入外呼展示主叫号码
     *
     * @param req 外呼号码参数
     * @return 外呼号码 ID
     */
    @Operation(summary = "录入外呼展示主叫号码")
    @PostMapping("/outbounds")
    public CommonResult<Long> createOutboundNumber(@Valid @RequestBody OutboundNumberCreateReq req) {
        Long id = resourceService.createOutboundNumber(req);
        return CommonResult.success(id);
    }

    /**
     * 查询外呼主叫池列表
     *
     * @param poolCode 号码池代码 (可选)
     * @return 外呼号码列表
     */
    @Operation(summary = "查询外呼主叫号码池列表")
    @GetMapping("/outbounds")
    public CommonResult<List<OutboundNumberVO>> listOutboundNumbers(
            @RequestParam(value = "poolCode", required = false) String poolCode) {
        List<OutboundNumberVO> list = resourceService.listOutboundNumbers(poolCode);
        return CommonResult.success(list);
    }

    /**
     * 查询通信集群节点状态与负载
     *
     * @return 通信节点列表
     */
    @Operation(summary = "查询FreeSWITCH通信节点集群监控状态")
    @GetMapping("/nodes")
    public CommonResult<List<TelephonyNodeVO>> listTelephonyNodes() {
        List<TelephonyNodeVO> list = resourceService.listTelephonyNodes();
        return CommonResult.success(list);
    }
}
