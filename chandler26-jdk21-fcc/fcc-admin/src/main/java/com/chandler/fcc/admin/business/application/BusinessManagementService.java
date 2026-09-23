package com.chandler.fcc.admin.business.application;

import cn.dev33.satoken.stp.StpUtil;
import com.chandler.fcc.admin.business.controller.req.CreateDialJobReq;
import com.chandler.fcc.admin.business.controller.req.SaveCustomerReq;
import com.chandler.fcc.admin.business.infrastructure.FccBusinessClient;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 管理台客户资料与自动外呼用例入口。
 */
@Service
@RequiredArgsConstructor
public class BusinessManagementService {

    private static final String INTERNAL_BASE = "/internal/business";

    private final FccBusinessClient client;

    /**
     * 分页查询当前管理员有权查看的客户摘要。
     *
     * @param page 页码
     * @param owner 负责坐席筛选
     * @param phone 电话筛选
     * @return 客户摘要数组
     */
    public Object customers(int page, String owner, String phone) {
        checkPermission();
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("page", page);
        query.put("owner", owner);
        query.put("phone", phone);
        return client.exchange("GET", INTERNAL_BASE + "/customers", query, null);
    }

    /**
     * 查询客户详情。
     *
     * @param id 客户标识
     * @return 客户详情
     */
    public Object customer(String id) {
        checkPermission();
        return client.exchange("GET", INTERNAL_BASE + "/customers/" + id, Map.of(), null);
    }

    /**
     * 创建客户资料。
     *
     * @param owner 负责坐席工号
     * @param request 客户资料
     * @return 新客户标识
     */
    public Object createCustomer(String owner, SaveCustomerReq request) {
        checkPermission();
        return client.exchange("POST", INTERNAL_BASE + "/customers", Map.of("owner", owner), request);
    }

    /**
     * 按版本修改客户资料。
     *
     * @param id 客户标识
     * @param request 客户资料及版本
     * @return 客户标识
     */
    public Object updateCustomer(String id, SaveCustomerReq request) {
        checkPermission();
        return client.exchange("PUT", INTERNAL_BASE + "/customers/" + id, Map.of(), request);
    }

    /**
     * 分页查询自动外呼任务。
     *
     * @param page 页码
     * @param owner 执行坐席筛选
     * @return 任务摘要数组
     */
    public Object jobs(int page, String owner) {
        checkPermission();
        return client.exchange("GET", INTERNAL_BASE + "/dial-jobs", Map.of("page", page, "owner", owner == null ? "" : owner), null);
    }

    /**
     * 创建自动外呼任务。
     *
     * @param request 调度参数
     * @return 任务标识
     */
    public Object createJob(CreateDialJobReq request) {
        checkPermission();
        return client.exchange("POST", INTERNAL_BASE + "/dial-jobs", Map.of(), request);
    }

    /**
     * 查询任务逐次尝试记录。
     *
     * @param id 任务标识
     * @return 尝试记录数组
     */
    public Object attempts(String id) {
        checkPermission();
        return client.exchange("GET", INTERNAL_BASE + "/dial-jobs/" + id + "/attempts", Map.of(), null);
    }

    /**
     * 暂停、恢复或取消任务。
     *
     * @param id 任务标识
     * @param action 操作类型
     * @return 操作结果
     */
    public Object control(String id, String action) {
        checkPermission();
        return client.exchange("POST", INTERNAL_BASE + "/dial-jobs/" + id + "/" + action, Map.of(), null);
    }

    /**
     * 校验客户和自动外呼管理权限。
     */
    private void checkPermission() {
        StpUtil.checkPermission("business:manage");
    }
}
