package com.chandler.fcc.server.management.controller;

import com.chandler.fcc.server.management.application.BusinessManagementService;
import com.chandler.fcc.server.management.controller.req.CreateDialJobReq;
import com.chandler.fcc.server.management.controller.req.SaveCustomerReq;
import com.chandler.fcc.server.management.controller.resp.ManagementResp;
import jakarta.validation.Valid;
import java.util.Map;
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
 * fcc-admin 使用的内部运行业务入口，所有用例均在线验证管理身份。
 *
 * <p>浏览器不得直接访问本入口；对外管理 API 统一由 fcc-admin 暴露。</p>
 */
@RestController
@RequestMapping("/internal/business")
@RequiredArgsConstructor
public class InternalBusinessController {

    private final BusinessManagementService service;

    /**
     * 统一成功信封。
     *
     * @param value 结果
     * @return 响应
     */
    private <T> ManagementResp<T> ok(T value) {
        var response = new ManagementResp<T>();
        response.setData(value);
        return response;
    }

    /**
     * 客户摘要。
     *
     * @param page 页码
     * @param flowKey 流程编码过滤
     * @param phone 号码过滤
     * @return 列表
     */
    @GetMapping("/customers")
    public ManagementResp<?> customers(
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(required = false) String owner,
        @RequestParam(required = false) String phone
    ) {
        return ok(service.customers(page, owner, phone));
    }

    /**
     * 客户详情。
     *
     * @param id 客户
     * @return 详情
     */
    @GetMapping("/customers/{id}")
    public ManagementResp<?> customer(@PathVariable String id) {
        return ok(service.customer(id));
    }

    /**
     * 创建客户。
     *
     * @param owner 负责坐席
     * @param record 资料
     * @return ID
     */
    @PostMapping("/customers")
    public ManagementResp<?> create(
        @RequestParam String owner,
        @Valid @RequestBody SaveCustomerReq request
    ) {
        return ok(
            service.save(
                null,
                owner,
                request.getName(),
                request.getPhoneNumber(),
                request.getCompanyName(),
                request.getNotes(),
                request.getVersion()
            )
        );
    }

    /**
     * 编辑客户。
     *
     * @param id 客户
     * @param record 资料与版本
     * @return ID
     */
    @PutMapping("/customers/{id}")
    public ManagementResp<?> update(
        @PathVariable String id,
        @Valid @RequestBody SaveCustomerReq request
    ) {
        return ok(
            service.save(
                id,
                null,
                request.getName(),
                request.getPhoneNumber(),
                request.getCompanyName(),
                request.getNotes(),
                request.getVersion()
            )
        );
    }

    /**
     * 外呼摘要。
     *
     * @param page 页码
     * @param owner 工号过滤
     * @return 列表
     */
    @GetMapping("/dial-jobs")
    public ManagementResp<?> jobs(
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(required = false) String flowKey
    ) {
        return ok(service.jobs(page, flowKey));
    }

    /**
     * 创建任务。
     *
     * @param request 调度参数
     * @return ID
     */
    @PostMapping("/dial-jobs")
    public ManagementResp<?> createJob(@Valid @RequestBody CreateDialJobReq request) {
        return ok(
            service.createJob(
                request.getNumber(),
                request.getFlowKey(),
                request.getVariables(),
                request.getMaxAttempts(),
                request.getRequestKey()
            )
        );
    }

    /**
     * 尝试详情。
     *
     * @param id 任务
     * @return 逐次结果
     */
    @GetMapping("/dial-jobs/{id}/attempts")
    public ManagementResp<?> attempts(@PathVariable String id) {
        return ok(service.attempts(id));
    }

    /**
     * 任务操作。
     *
     * @param id 任务
     * @param action 操作
     * @return 结果
     */
    @PostMapping("/dial-jobs/{id}/{action}")
    public ManagementResp<?> control(@PathVariable String id, @PathVariable String action) {
        service.control(id, action);
        return ok(Map.of("status", "UPDATED"));
    }
}
