package com.chandler.fcc.admin.controller;

import com.chandler.fcc.admin.model.CommonResult;
import com.chandler.fcc.admin.model.dto.FlowPublishReq;
import com.chandler.fcc.admin.model.dto.FlowSaveDraftReq;
import com.chandler.fcc.admin.model.dto.FlowSimulateReq;
import com.chandler.fcc.admin.model.vo.FlowDefinitionVO;
import com.chandler.fcc.admin.model.vo.FlowSimulateRespVO;
import com.chandler.fcc.admin.model.vo.FlowVersionVO;
import com.chandler.fcc.admin.service.FlowDefinitionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 通话流程定义与多分支版本管理 REST 控制器
 *
 * @author Chandler
 */
@Tag(name = "IVR流程与版本管理", description = "提供系统核心通话流查询、版本切换、草稿保存、热发布上线与多分支仿真推演接口")
@RestController
@RequestMapping("/api/admin/flows")
@RequiredArgsConstructor
public class FlowDefinitionController {

    private final FlowDefinitionService flowService;

    /**
     * 查询所有系统通话流程定义列表
     */
    @Operation(summary = "查询系统通话流程定义列表")
    @GetMapping
    public CommonResult<List<FlowDefinitionVO>> listFlows() {
        List<FlowDefinitionVO> list = flowService.listFlows();
        return CommonResult.success(list);
    }

    /**
     * 查询指定流程的所有版本列表
     */
    @Operation(summary = "查询指定流程版本列表")
    @GetMapping("/{flowKey}/versions")
    public CommonResult<List<FlowVersionVO>> getVersions(@PathVariable("flowKey") String flowKey) {
        List<FlowVersionVO> list = flowService.getVersions(flowKey);
        return CommonResult.success(list);
    }

    /**
     * 保存流程草稿配置
     */
    @Operation(summary = "保存流程草稿配置")
    @PostMapping("/{flowKey}/draft")
    public CommonResult<String> saveDraft(@PathVariable("flowKey") String flowKey,
                                         @Valid @RequestBody FlowSaveDraftReq req) {
        String ver = flowService.saveDraft(flowKey, req);
        return CommonResult.success(ver);
    }

    /**
     * 发布新版本上线
     */
    @Operation(summary = "发布新版本上线")
    @PostMapping("/{flowKey}/publish")
    public CommonResult<String> publishFlow(@PathVariable("flowKey") String flowKey,
                                           @Valid @RequestBody FlowPublishReq req) {
        String ver = flowService.publishFlow(flowKey, req);
        return CommonResult.success(ver);
    }

    /**
     * 流程多分支仿真模拟运行推演
     */
    @Operation(summary = "流程仿真模拟运行推演")
    @PostMapping("/simulate")
    public CommonResult<FlowSimulateRespVO> simulateFlow(@RequestBody FlowSimulateReq req) {
        FlowSimulateRespVO resp = flowService.simulateFlow(req);
        return CommonResult.success(resp);
    }
}
