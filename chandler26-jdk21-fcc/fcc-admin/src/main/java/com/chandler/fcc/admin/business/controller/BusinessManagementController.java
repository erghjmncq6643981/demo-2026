package com.chandler.fcc.admin.business.controller;

import com.chandler.fcc.admin.business.application.BusinessManagementService;
import com.chandler.fcc.admin.business.controller.req.CreateDialJobReq;
import com.chandler.fcc.admin.business.controller.req.SaveCustomerReq;
import com.chandler.fcc.admin.model.CommonResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
 * 管理台客户资料与自动外呼 REST 入口。
 */
@Tag(name = "客户与自动外呼管理", description = "管理客户资料和自动外呼调度任务")
@RestController
@RequestMapping("/api/admin/business")
@RequiredArgsConstructor
public class BusinessManagementController {

    private final BusinessManagementService service;

    /**
     * 查询客户摘要。
     *
     * @param page 页码
     * @param owner 负责坐席筛选
     * @param phone 电话筛选
     * @return 客户摘要
     */
    @GetMapping("/customers")
    @Operation(summary = "分页查询客户资料")
    public CommonResult<Object> customers(
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(required = false) String owner,
        @RequestParam(required = false) String phone
    ) {
        return CommonResult.success(service.customers(page, owner, phone));
    }

    /**
     * 查询客户详情。
     *
     * @param id 客户标识
     * @return 客户详情
     */
    @GetMapping("/customers/{id}")
    @Operation(summary = "查询客户详情")
    public CommonResult<Object> customer(@PathVariable String id) {
        return CommonResult.success(service.customer(id));
    }

    /**
     * 创建客户资料。
     *
     * @param owner 负责坐席工号
     * @param request 客户资料
     * @return 新客户标识
     */
    @PostMapping("/customers")
    @Operation(summary = "创建客户资料")
    public CommonResult<Object> createCustomer(
        @RequestParam String owner,
        @Valid @RequestBody SaveCustomerReq request
    ) {
        return CommonResult.success(service.createCustomer(owner, request));
    }

    /**
     * 修改客户资料。
     *
     * @param id 客户标识
     * @param request 客户资料及版本
     * @return 客户标识
     */
    @PutMapping("/customers/{id}")
    @Operation(summary = "按版本修改客户资料")
    public CommonResult<Object> updateCustomer(
        @PathVariable String id,
        @Valid @RequestBody SaveCustomerReq request
    ) {
        return CommonResult.success(service.updateCustomer(id, request));
    }

    /**
     * 查询自动外呼任务。
     *
     * @param page 页码
     * @param flowKey 流程编码筛选
     * @return 任务摘要
     */
    @GetMapping("/dial-jobs")
    @Operation(summary = "分页查询自动外呼任务")
    public CommonResult<Object> jobs(
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(required = false) String flowKey
    ) {
        return CommonResult.success(service.jobs(page, flowKey));
    }

    /**
     * 创建自动外呼任务。
     *
     * @param request 调度参数
     * @return 任务标识
     */
    @PostMapping("/dial-jobs")
    @Operation(summary = "创建自动外呼任务")
    public CommonResult<Object> createJob(@Valid @RequestBody CreateDialJobReq request) {
        return CommonResult.success(service.createJob(request));
    }

    /**
     * 查询任务尝试记录。
     *
     * @param id 任务标识
     * @return 逐次尝试记录
     */
    @GetMapping("/dial-jobs/{id}/attempts")
    @Operation(summary = "查询自动外呼逐次结果")
    public CommonResult<Object> attempts(@PathVariable String id) {
        return CommonResult.success(service.attempts(id));
    }

    /**
     * 暂停、恢复或取消任务。
     *
     * @param id 任务标识
     * @param action 操作类型
     * @return 操作结果
     */
    @PostMapping("/dial-jobs/{id}/{action}")
    @Operation(summary = "变更自动外呼任务状态")
    public CommonResult<Object> control(@PathVariable String id, @PathVariable String action) {
        return CommonResult.success(service.control(id, action));
    }
}
