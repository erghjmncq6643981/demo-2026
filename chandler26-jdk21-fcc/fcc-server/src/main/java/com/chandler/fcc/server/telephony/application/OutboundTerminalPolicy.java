package com.chandler.fcc.server.telephony.application;

import com.chandler.fcc.common.enums.CallStageState;

/** 外呼终态映射的纯策略；不推断话道已结束，不执行持久化或释放。 */
final class OutboundTerminalPolicy {
    /** 禁止构造无状态策略。 */
    private OutboundTerminalPolicy() {}

    /**
     * 保留既有结束原因到业务阶段的映射。
     * @param cause 已从源事件读取的结束原因
     * @return 未知恢复为异常结束，其余保持原正常结束语义
     */
    static CallStageState stageFor(String cause) {
        return "RECOVERY_UNKNOWN".equals(cause) ? CallStageState.ERROR_END : CallStageState.NORMAL_END;
    }
}
