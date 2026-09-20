package com.chandler.fcc.admin.flow;

import cn.dev33.satoken.stp.StpUtil;
import com.chandler.fcc.admin.flow.controller.resp.FlowExecutionResp;
import com.chandler.fcc.common.util.IdUtil;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * 流程管理及执行事实查询，不访问运行端内存或推测历史路径。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FlowStudioService {

    private final FlowStudioMapper mapper;

    /**
     * 创建无演示配置的呼入流程。
     *
     * @param key 业务键
     * @param name 中文名称
     * @return 新流程键
     */
    public String create(String key, String name) {
        StpUtil.checkPermission("flow:write");
        long tenant = tenant();
        if (
            key == null ||
            !key.matches("[A-Za-z][A-Za-z0-9_-]{0,63}") ||
            key.startsWith("SYSTEM_") ||
            name == null ||
            name.isBlank() ||
            name.length() > 128
        ) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "流程代码或名称不合法");
        mapper.create(Map.of("id", IdUtil.nextId(), "tenant", tenant, "key", key, "name", name.trim()));
        log.info("[流程管理] 新建流程 tenantId={} flowKey={}", tenant, key);
        return key;
    }

    /**
     * 读取本租户通话的版本快照与分页阶段事实。
     *
     * @param call 通话
     * @param after 游标
     * @return 真实执行记录
     */
    public FlowExecutionResp execution(String call, String after) {
        StpUtil.checkPermission("flow:view");
        long tenant = tenant();
        if (
            !call.matches("[1-9][0-9]{0,18}") || !after.matches("[0-9]{1,19}")
        ) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "标识不合法");
        if (mapper.callExists(tenant, call) == 0) throw new ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "通话不存在"
        );
        var instance = mapper.instance(tenant, call);
        var steps = mapper.steps(tenant, call, after);
        var response = new FlowExecutionResp();
        response.setInstance(instance == null ? Map.of() : instance);
        response.setSteps(steps);
        response.setNextCursor(steps.size() == 100 ? steps.getLast().get("id").toString() : "");
        return response;
    }

    /**
     * 从服务端登录会话获取租户，不接受浏览器指定。
     *
     * @return 租户 ID
     */
    public static long tenant() {
        StpUtil.checkLogin();
        Object value = StpUtil.getSession().get("tenantId");
        if (!(value instanceof Number tenant)) throw new ResponseStatusException(
            HttpStatus.FORBIDDEN,
            "登录身份缺少租户"
        );
        return tenant.longValue();
    }
}
