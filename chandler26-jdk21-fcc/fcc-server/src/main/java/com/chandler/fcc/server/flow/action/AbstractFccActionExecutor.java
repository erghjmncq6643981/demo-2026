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
}
