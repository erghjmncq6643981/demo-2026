package com.chandler.fcc.server.management.controller;

import com.chandler.fcc.server.customer.api.CustomerRecord;
import com.chandler.fcc.server.management.BusinessManagementService;
import com.chandler.fcc.server.management.controller.req.CreateDialJobReq;
import com.chandler.fcc.server.management.controller.resp.ManagementResp;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 管理前端使用的运行业务入口，所有用例在线验证管理员及租户。
 */
@RestController
@RequestMapping("/api/telephony/management")
@RequiredArgsConstructor
public class BusinessManagementController {

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
     * @param owner 工号过滤
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
    public ManagementResp<?> create(@RequestParam String owner, @RequestBody CustomerRecord record) {
        return ok(service.save(null, owner, record));
    }

    /**
     * 编辑客户。
     *
     * @param id 客户
     * @param record 资料与版本
     * @return ID
     */
    @PutMapping("/customers/{id}")
    public ManagementResp<?> update(@PathVariable String id, @RequestBody CustomerRecord record) {
        return ok(service.save(id, null, record));
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
        @RequestParam(required = false) String owner
    ) {
        return ok(service.jobs(page, owner));
    }

    /**
     * 创建任务。
     *
     * @param request 调度参数
     * @return ID
     */
    @PostMapping("/dial-jobs")
    public ManagementResp<?> createJob(@RequestBody CreateDialJobReq request) {
        return ok(
            service.createJob(
                request.getOwner(),
                request.getNumber(),
                request.getMode(),
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
