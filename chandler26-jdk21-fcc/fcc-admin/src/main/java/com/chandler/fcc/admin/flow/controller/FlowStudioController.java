package com.chandler.fcc.admin.flow.controller;

import com.chandler.fcc.admin.flow.application.FlowStudioService;
import com.chandler.fcc.admin.flow.controller.req.CreateFlowReq;
import com.chandler.fcc.admin.flow.controller.req.FlowPageReq;
import com.chandler.fcc.admin.flow.controller.req.PublishFlowReq;
import com.chandler.fcc.admin.flow.controller.req.SaveFlowDraftReq;
import com.chandler.fcc.admin.flow.controller.resp.FlowActionResp;
import com.chandler.fcc.admin.flow.controller.resp.FlowExecutionResp;
import com.chandler.fcc.admin.flow.controller.resp.FlowPublishResp;
import com.chandler.fcc.admin.flow.controller.resp.FlowSummaryResp;
import com.chandler.fcc.admin.flow.controller.resp.FlowVersionResp;
import com.chandler.fcc.admin.flow.controller.resp.SystemFlowModelResp;
import com.chandler.fcc.admin.model.CommonResult;
import com.chandler.fcc.admin.model.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * IVR Flow Model 的统一维护、发布和执行轨迹接口。
 */
@Tag(name = "IVR Flow Studio", description = "维护 IVR 模型、不可变版本和通话实际执行轨迹")
@RestController
@RequestMapping("/api/admin/flow-studio")
@RequiredArgsConstructor
public class FlowStudioController {

    private final FlowStudioService service;

    /**
     * 查询公共动作和三类执行器目录。
     *
     * @return 动作目录
     */
    @Operation(summary = "查询流程动作与执行器目录")
    @GetMapping("/actions")
    public CommonResult<List<FlowActionResp>> actions() {
        return CommonResult.success(service.actions());
    }

    /**
     * 查询所有固定通话模型。
     *
     * @return 固定模型目录
     */
    @Operation(summary = "查询固定通话模型目录")
    @GetMapping("/models")
    public CommonResult<List<SystemFlowModelResp>> models() {
        return CommonResult.success(service.systemModels());
    }

    /**
     * 查询一个固定通话模型详情。
     *
     * @param template 固定模板代码
     * @return 完整模型
     */
    @Operation(summary = "查询固定通话模型详情")
    @GetMapping("/models/{template}")
    public CommonResult<SystemFlowModelResp> model(@PathVariable String template) {
        return CommonResult.success(service.systemModel(template));
    }

    /**
     * 查询流程摘要列表。
     *
     * @return 流程摘要
     */
    @Operation(summary = "查询 IVR 流程列表")
    @GetMapping("/flows")
    public CommonResult<PageResult<FlowSummaryResp>> flows(@Valid FlowPageReq request) {
        return CommonResult.success(service.listFlows(request));
    }

    /**
     * 创建流程主数据。
     *
     * @param request 创建参数
     * @return 新流程摘要
     */
    @Operation(summary = "创建 IVR 流程")
    @PostMapping("/flows")
    public CommonResult<FlowSummaryResp> create(@Valid @RequestBody CreateFlowReq request) {
        return CommonResult.success(service.create(request));
    }

    /**
     * 查询单个流程详情。
     *
     * @param flowKey 稳定流程代码
     * @return 流程摘要
     */
    @Operation(summary = "查询 IVR 流程详情")
    @GetMapping("/flows/{flowKey}")
    public CommonResult<FlowSummaryResp> flow(@PathVariable String flowKey) {
        return CommonResult.success(service.flow(flowKey));
    }

    /**
     * 查询流程版本摘要列表。
     *
     * @param flowKey 稳定流程代码
     * @return 版本摘要
     */
    @Operation(summary = "查询 IVR 流程版本列表")
    @GetMapping("/flows/{flowKey}/versions")
    public CommonResult<PageResult<FlowVersionResp>> versions(
        @PathVariable String flowKey,
        @Valid FlowPageReq request
    ) {
        return CommonResult.success(service.versions(flowKey, request));
    }

    /**
     * 按需查询完整版本定义。
     *
     * @param flowKey 稳定流程代码
     * @param versionNo 递增版本序号
     * @return 完整版本定义
     */
    @Operation(summary = "查询 IVR 流程版本详情")
    @GetMapping("/flows/{flowKey}/versions/{versionNo}")
    public CommonResult<FlowVersionResp> version(
        @PathVariable String flowKey,
        @PathVariable int versionNo
    ) {
        return CommonResult.success(service.version(flowKey, versionNo));
    }

    /**
     * 保存流程草稿。
     *
     * @param flowKey 稳定流程代码
     * @param request 完整流程定义
     * @return 草稿版本详情
     */
    @Operation(summary = "保存 IVR 流程草稿")
    @PutMapping("/flows/{flowKey}/draft")
    public CommonResult<FlowVersionResp> saveDraft(
        @PathVariable String flowKey,
        @Valid @RequestBody SaveFlowDraftReq request
    ) {
        return CommonResult.success(service.saveDraft(flowKey, request));
    }

    /**
     * 发布流程草稿。
     *
     * @param flowKey 稳定流程代码
     * @param request 发布参数
     * @return 发布与运行端激活状态
     */
    @Operation(summary = "发布 IVR 流程草稿")
    @PostMapping("/flows/{flowKey}/publish")
    public CommonResult<FlowPublishResp> publish(
        @PathVariable String flowKey,
        @Valid @RequestBody PublishFlowReq request
    ) {
        return CommonResult.success(service.publish(flowKey, request));
    }

    /**
     * 查看单通话阶段事实。
     *
     * @param callId 通话 ID
     * @param after 上页末尾记录 ID
     * @return 快照与阶段列表
     */
    @Operation(summary = "查询单通话流程执行轨迹")
    @GetMapping("/calls/{callId}")
    public CommonResult<FlowExecutionResp> execution(
        @PathVariable String callId,
        @RequestParam(defaultValue = "0") String after
    ) {
        return CommonResult.success(service.execution(callId, after));
    }
}
