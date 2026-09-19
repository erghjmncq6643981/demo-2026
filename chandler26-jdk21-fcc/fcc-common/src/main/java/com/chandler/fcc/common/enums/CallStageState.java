package com.chandler.fcc.common.enums;

import lombok.Getter;

/**
 * 呼叫生命周期阶段状态枚举
 * <p>
 * 表示一通通话业务在状态机流转中所处的生命周期阶段。
 * 状态迁移具有单调性与幂等性保障。
 * </p>
 *
 * @author Chandler
 */
@Getter
public enum CallStageState {

    /**
     * 呼叫初始化阶段
     */
    INIT("呼叫初始化"),

    /**
     * 呼叫创建 / 来电进入
     */
    START("来电接入/呼叫创建"),

    /**
     * 外呼发起中
     */
    CALLING("外呼呼叫中"),

    /**
     * 对端振铃中
     */
    RINGING("振铃中"),

    /**
     * 话道就绪，执行路由排队/分配坐席
     */
    ROUTE("路由与排队"),

    /**
     * 双方接通桥接对讲中
     */
    CONNECTED("双方接通"),

    /**
     * 正常挂机结束
     */
    NORMAL_END("正常结束"),

    /**
     * 异常或失败结束
     */
    ERROR_END("异常挂断");

    /**
     * 阶段状态中文业务描述
     */
    private final String desc;

    /**
     * 构造呼叫阶段状态枚举
     *
     * @param desc 阶段状态中文业务描述
     */
    CallStageState(String desc) {
        this.desc = desc;
    }

    /**
     * 校验当前状态是否为终态
     *
     * @return 若为终态返回 true，否则返回 false
     */
    public boolean isTerminal() {
        return this == NORMAL_END || this == ERROR_END;
    }
}
