package com.chandler.fcc.common.enums;

import lombok.Getter;

/**
 * 固定通话模型允许声明的业务动作目录。
 *
 * <p>管理端使用该目录校验模型，运行端使用同一目录声明执行能力，避免两侧各自维护字符串。</p>
 */
@Getter
public enum FlowActionType {

    RESOLVE_DID_AND_PARK("解析 DID 并接管呼入"),
    READ_DTMF("播放提示并收取按键"),
    SELECT_DIGIT_ROUTE("根据按键选择路由分支"),
    RESERVE_AND_DIAL_AGENT("预占并呼叫可用坐席"),
    CHANNEL_BRIDGE("桥接客户与坐席话道"),
    WAIT_FOR_HANGUP("等待通话挂机事件"),
    FINALIZE_INBOUND("结算呼入并按需生成回拨"),
    VALIDATE_ROUTE_AND_RESERVE_AGENT("校验外呼路由并预占坐席"),
    DIAL_AGENT("呼叫坐席话机"),
    DIAL_CUSTOMER("呼叫客户号码"),
    FINALIZE_OUTBOUND("结算双向外呼"),
    VALIDATE_NOTIFICATION_AND_ROUTE("校验通知任务和出局路由"),
    PERSIST_CONFIRMATION_AND_HANGUP("保存通知确认并挂机"),
    FINALIZE_NOTIFICATION("结算通知外呼"),
    VALIDATE_BINDING_EXTENSION("校验发起绑定的节点和分机"),
    BIND_AGENT_EXTENSION("将输入工号绑定到当前分机"),
    HANGUP_BINDING_CHANNEL("结束话机绑定通话");

    private final String desc;

    /**
     * 创建业务动作定义。
     *
     * @param desc 中文业务描述
     */
    FlowActionType(String desc) {
        this.desc = desc;
    }

    /**
     * 严格解析模型动作代码。
     *
     * @param code 动作代码
     * @return 动作枚举
     * @throws IllegalArgumentException 动作未在公共目录定义
     */
    public static FlowActionType fromCode(String code) {
        if (code == null || code.isBlank()) throw new IllegalArgumentException("流程动作不能为空");
        try {
            return valueOf(code);
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("流程包含未定义动作: " + code, failure);
        }
    }
}
