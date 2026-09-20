package com.chandler.fcc.admin.flow;

import com.chandler.fcc.admin.flow.api.request.CreateFlowRequest;
import com.chandler.fcc.admin.model.CommonResult;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 管理画布的新建入口与只读通话过程入口。
 */
@RestController
@RequestMapping("/api/admin/flow-studio")
@RequiredArgsConstructor
public class FlowStudioController {

    private final FlowStudioService service;

    /**
     * 创建流程元数据。
     *
     * @param request 创建参数
     * @return 流程代码
     */
    @PostMapping
    public CommonResult<String> create(@RequestBody CreateFlowRequest request) {
        return CommonResult.success(service.create(request.getFlowKey(), request.getFlowName()));
    }

    /**
     * 查看单通话阶段事实。
     *
     * @param callId 通话 ID
     * @param after 上页末尾记录 ID
     * @return 快照与阶段列表
     */
    @GetMapping("/calls/{callId}")
    public CommonResult<Map<String, Object>> execution(
        @PathVariable String callId,
        @RequestParam(defaultValue = "0") String after
    ) {
        return CommonResult.success(service.execution(callId, after));
    }
}
