package com.chandler.fcc.admin.controller;

import com.chandler.fcc.admin.model.CommonResult;
import com.chandler.fcc.admin.model.PageResult;
import com.chandler.fcc.admin.model.dto.CallbackTaskAssignReq;
import com.chandler.fcc.admin.model.dto.CallbackTaskQueryReq;
import com.chandler.fcc.admin.model.vo.CallbackTaskVO;
import com.chandler.fcc.admin.service.CallbackTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 未接待漏话待办与回拨总池 REST 控制器
 *
 * @author Chandler
 */
@Tag(name = "未接待漏话待办总池", description = "提供漏话任务分页检索、坐席指派与一键回拨调度接口")
@RestController
@RequestMapping("/api/admin/callbacks")
@RequiredArgsConstructor
public class CallbackTaskController {

    private final CallbackTaskService callbackTaskService;

    /**
     * 分页查询漏话待办任务
     */
    @Operation(summary = "分页查询漏话待办任务")
    @GetMapping
    public CommonResult<PageResult<CallbackTaskVO>> queryCallbacks(CallbackTaskQueryReq req) {
        PageResult<CallbackTaskVO> result = callbackTaskService.queryCallbacks(req);
        return CommonResult.success(result);
    }

    /**
     * 指派跟进坐席
     */
    @Operation(summary = "指派漏话跟进坐席")
    @PostMapping("/{id}/assign")
    public CommonResult<Void> assignTask(@PathVariable("id") Long id,
                                         @Valid @RequestBody CallbackTaskAssignReq req) {
        callbackTaskService.assignTask(id, req);
        return CommonResult.success();
    }

}
