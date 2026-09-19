package com.chandler.fcc.common.enums;

import lombok.Getter;

/**
 * 呼叫中心动作类型枚举
 * <p>
 * 定义控制面可下发至 FreeSWITCH/Sidecar 的底层动作以及编排引擎内部调度动作。
 * 每个动作包含关联的 FNode 指令方法名、标识符及中文描述。
 * </p>
 *
 * @author Chandler
 */
@Getter
public enum ActionType {

    /**
     * 呼叫开始动作
     */
    START("FNode.Start", "start", "呼叫开始"),

    /**
     * 空动作（占位）
     */
    EMPTY("FNode.Empty", "", "空动作"),

    /**
     * 呼叫结束动作
     */
    END("FNode.End", "end", "呼叫结束"),

    /**
     * 排队与技能组路由动作
     */
    ROUTE("FNode.Route", "route", "技能组路由排队"),

    /**
     * 直连坐席路由动作
     */
    DIRECT_ROUTE("FNode.Route", "direct_route", "指定坐席直连路由"),

    /**
     * 发起外呼坐席
     */
    DIAL_AGENT("FNode.Dial", "dial-agent", "外呼坐席话道"),

    /**
     * 发起外呼客户
     */
    DIAL_GUEST("FNode.Dial", "dial-guest", "外呼客户话道"),

    /**
     * 话道双方桥接对讲
     */
    CHANNEL_BRIDGE("FNode.ChannelBridge", "channel-bridge", "话道双向桥接"),

    /**
     * 启动通道双轨录音
     */
    RECORD("FNode.Record", "record", "启动通道录音"),

    /**
     * 停止通道录音
     */
    RECORD_STOP("FNode.Record", "record-stop", "停止通道录音"),

    /**
     * 放音播报语音
     */
    PLAY("FNode.Play", "play", "放音语音播报"),

    /**
     * DTMF 按键收号
     */
    READ_DTMF("FNode.ReadDTMF", "read-dtmf", "按键收号"),

    /**
     * IVR 导航按键分流
     */
    DTMF_NAVIGATION("FNode.ReadDTMF", "dtmf-navigation", "IVR导航按键分流"),

    /**
     * 满意度服务按键评价
     */
    DTMF_EVALUATION("FNode.ReadDTMF", "dtmf-evaluation", "满意度服务评价"),

    /**
     * 通话转接
     */
    TRANSFER("FNode.Transfer", "transfer", "通话转接"),

    /**
     * 挂机拆线（全通道）
     */
    HANGUP("FNode.Hangup", "hangup-all", "挂断所有话道"),

    /**
     * 单独挂断坐席侧话道
     */
    HANGUP_AGENT("FNode.Hangup", "hangup-agent", "挂断坐席侧话道"),

    /**
     * 单独挂断客户侧话道
     */
    HANGUP_GUEST("FNode.Hangup", "hangup-guest", "挂断客户侧话道");

    /**
     * 对应的底层 FNode JSON-RPC 指令方法名
     */
    private final String action;

    /**
     * 动作唯一英文标识符
     */
    private final String id;

    /**
     * 动作中文业务描述
     */
    private final String desc;

    /**
     * 构造动作类型枚举
     *
     * @param action 底层 FNode JSON-RPC 指令方法名
     * @param id     动作唯一英文标识符
     * @param desc   动作中文业务描述
     */
    ActionType(String action, String id, String desc) {
        this.action = action;
        this.id = id;
        this.desc = desc;
    }

    /**
     * 根据 FNode 动作指令字符串解析匹配的枚举值
     *
     * @param action 动作指令字符串（如 "FNode.Dial" 或 "DIAL_AGENT"）
     * @return 匹配的 ActionType 枚举，若无匹配则返回 EMPTY
     */
    public static ActionType valueOfAction(String action) {
        if (action == null || action.trim().isEmpty()) {
            return EMPTY;
        }
        for (ActionType type : ActionType.values()) {
            if (type.action.equalsIgnoreCase(action) || type.name().equalsIgnoreCase(action) || type.id.equalsIgnoreCase(action)) {
                return type;
            }
        }
        return EMPTY;
    }
}
