package com.chandler.fcc.server.flow;

import com.chandler.fcc.common.enums.CallStageState;

/**
 * 呼叫阶段处理器标准接口
 * <p>
 * 每个实现类专注于处理特定的呼叫阶段（如 START, CALLING, ROUTE, CONNECTED, NORMAL_END）。
 * </p>
 *
 * @author Chandler
 */
public interface ICallStageProcessor {

    /**
     * 获取当前处理器负责的呼叫生命周期阶段
     *
     * @return 阶段状态枚举
     */
    CallStageState getCallStage();
}
