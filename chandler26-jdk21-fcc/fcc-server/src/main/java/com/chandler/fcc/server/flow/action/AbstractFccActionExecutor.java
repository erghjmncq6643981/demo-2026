package com.chandler.fcc.server.flow.action;

import com.chandler.fcc.server.command.FccClient;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 抽象呼叫动作执行器基类
 * <p>
 * 为具体动作执行器提供统一的 FccClient 客户端注入与基础支撑。
 * </p>
 *
 * @author Chandler
 */
@Getter
public abstract class AbstractFccActionExecutor implements IFccAction {

    @Autowired
    private FccClient fccClient;

    /**
     * 读取必需的运行时参数，禁止以业务通话标识替代控制或话道标识。
     * @param node 执行节点
     * @param key 参数名称
     * @return 非空参数
     * @throws IllegalArgumentException 参数缺失时抛出
     */
    protected String requiredData(com.chandler.fcc.common.entity.FlowNode node, String key) {
        String value = node.getDataStr(key, null);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("流程动作缺少必需参数: " + key);
        }
        return value;
    }
}
