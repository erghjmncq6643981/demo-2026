package com.chandler.fcc.admin.controller;

import com.chandler.fcc.admin.model.CommonResult;
import com.chandler.fcc.admin.model.dto.DidNumberCreateReq;
import com.chandler.fcc.admin.model.dto.OutboundNumberCreateReq;
import com.chandler.fcc.admin.model.dto.TrunkCreateReq;
import com.chandler.fcc.admin.model.vo.DidNumberVO;
import com.chandler.fcc.admin.model.vo.OutboundNumberVO;
import com.chandler.fcc.admin.model.vo.TelephonyNodeVO;
import com.chandler.fcc.admin.model.vo.TrunkVO;
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
@Tag(name = "通信资源与网关管理", description = "提供SIP中继、DID呼入引示号、外呼主叫池及FreeSWITCH集群节点监控接口")
@RestController
@RequestMapping("/api/admin/resources")
@RequiredArgsConstructor
public class TelephonyResourceController {

    private final TelephonyResourceService resourceService;

    /**
     * 创建通信中继线路
     *
     * @param req 中继创建参数
     * @return 中继 ID
     */
    @Operation(summary = "创建SIP通信中继线路")
    @PostMapping("/trunks")
    public CommonResult<Long> createTrunk(@Valid @RequestBody TrunkCreateReq req) {
        Long id = resourceService.createTrunk(req);
        return CommonResult.success(id);
    }

    /**
     * 查询全量有效中继线路
     *
     * @return 中继列表
     */
    @Operation(summary = "查询中继线路列表")
    @GetMapping("/trunks")
    public CommonResult<List<TrunkVO>> listTrunks() {
        List<TrunkVO> list = resourceService.listTrunks();
        return CommonResult.success(list);
    }

    /**
     * 删除中继线路
     *
     * @param id 中继 ID
     * @return 操作成功响应
     */
    @Operation(summary = "删除中继线路")
    @DeleteMapping("/trunks/{id}")
    public CommonResult<Void> deleteTrunk(@PathVariable("id") Long id) {
        resourceService.deleteTrunk(id);
        return CommonResult.success();
    }

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
