package com.chandler.fcc.common.enums;

import com.chandler.fcc.common.protocol.FNodeMethod;
import java.util.Arrays;
import lombok.Getter;

/**
 * 固定通话模型允许声明的业务动作目录。
 *
 * <p>管理端使用该目录校验模型，运行端使用同一目录声明执行能力，避免两侧各自维护字符串。</p>
 */
@Getter
public enum FlowActionType {

    RESOLVE_DID_AND_PARK("解析 DID 并接管呼入", "inbound.resolve-did-and-park"),
    READ_DTMF("播放提示并收取按键", FNodeMethod.READ_DTMF),
    PLAY_MEDIA("播放媒体或导航语音", FNodeMethod.PLAY),
    PLAY_NAVIGATION_VOICE("播放导航语音", FNodeMethod.PLAY),
    COLLECT_SERVICE_RATING("收取预设服务评价", FNodeMethod.READ_DTMF),
    PLAY_CLOSING_VOICE("播放结束语音", FNodeMethod.PLAY),
    PERSIST_SERVICE_RATING("保存服务评价结果", "call.persist-service-rating"),
    START_RECORDING("开始通话录音", FNodeMethod.RECORD),
    STOP_RECORDING("停止通话录音", FNodeMethod.RECORD),
    SELECT_DIGIT_ROUTE("根据按键选择路由分支", "inbound.select-digit-route"),
    RESERVE_AND_DIAL_AGENT("预占并呼叫可用坐席", "inbound.reserve-and-dial-agent"),
    CHANNEL_BRIDGE("桥接客户与坐席话道", FNodeMethod.CHANNEL_BRIDGE),
    TRANSFER_CALL("转接当前通话", FNodeMethod.TRANSFER),
    HANGUP_CALL("结束当前通话", FNodeMethod.HANGUP),
    WAIT_FOR_HANGUP("等待通话挂机事件", "call.wait-for-hangup"),
    FINALIZE_INBOUND("结算呼入并按需生成回拨", "inbound.finalize"),
    VALIDATE_ROUTE_AND_RESERVE_AGENT("校验外呼路由并预占坐席", "outbound.validate-and-reserve"),
    ACCEPT_AGENT_ORIGINATED_CALL("接管坐席终端主动外呼话道", "outbound.accept-agent-originated"),
    DIAL_AGENT("呼叫坐席话机", FNodeMethod.DIAL),
    DIAL_CUSTOMER("呼叫客户号码", FNodeMethod.DIAL),
    FINALIZE_OUTBOUND("结算双向外呼", "outbound.finalize"),
    VALIDATE_NOTIFICATION_AND_ROUTE("校验通知任务和出局路由", "notification.validate-and-route"),
    PERSIST_CONFIRMATION_AND_HANGUP("保存通知确认并挂机", "notification.confirm-and-hangup"),
    FINALIZE_NOTIFICATION("结算通知外呼", "notification.finalize"),
    VALIDATE_BINDING_EXTENSION("校验发起绑定的 SIP 分机", "binding.validate-extension"),
    ANSWER_BINDING_CHANNEL("应答话机绑定话道", FNodeMethod.ANSWER),
    BIND_AGENT_EXTENSION("将输入工号绑定到当前分机", "binding.bind-agent-extension"),
    PLAY_BINDING_RESULT("播报话机绑定结果", FNodeMethod.PLAY),
    HANGUP_BINDING_CHANNEL("结束话机绑定通话", FNodeMethod.HANGUP),
    QUERY_COMMAND_RESULT("查询既有指令结果", FNodeMethod.COMMAND_RESULT),
    QUERY_CHANNEL_SNAPSHOT("查询当前话道快照", FNodeMethod.CHANNEL_SNAPSHOT),
    QUERY_NODE_STATUS("查询软交换节点状态", FNodeMethod.STATUS),
    DRAIN_NODE("排空软交换节点", FNodeMethod.DRAIN),
    RESUME_NODE("恢复软交换节点接入", FNodeMethod.RESUME),
    EXECUTE_NATIVE_API("执行受控原生指令", FNodeMethod.NATIVE_API),
    INVOKE_CONFIGURED_THIRD_PARTY(
        "调用已配置的第三方接口",
        FlowActionExecutorType.THIRD_PARTY_API,
        "third-party.invoke"
    );

    private final String desc;
    private final FlowActionExecutorType executorType;
    private final String operation;
    private final FNodeMethod fNodeMethod;

    /**
     * 创建业务动作定义。
     *
     * @param desc 中文业务描述
     * @param operation 稳定内部操作代码
     */
    FlowActionType(String desc, String operation) {
        this(desc, FlowActionExecutorType.INTERNAL_METHOD, operation, null);
    }

    /**
     * 创建 FNode 指令动作定义。
     *
     * @param desc 中文业务描述
     * @param method 规范 FNode 方法
     */
    FlowActionType(String desc, FNodeMethod method) {
        this(desc, FlowActionExecutorType.FNODE_COMMAND, method.getWireName(), method);
    }

    /**
     * 创建显式执行边界的动作定义。
     *
     * @param desc 中文业务描述
     * @param executorType 执行器类型
     * @param operation 稳定操作代码
     */
    FlowActionType(String desc, FlowActionExecutorType executorType, String operation) {
        this(desc, executorType, operation, null);
    }

    /**
     * 创建带有明确 FNode 方法对象的动作定义。
     *
     * @param desc 中文业务描述
     * @param executorType 执行器类型
     * @param operation 稳定操作代码
     * @param fNodeMethod 对应的 FNode 方法；非 FNode 动作为空
     */
    FlowActionType(
        String desc,
        FlowActionExecutorType executorType,
        String operation,
        FNodeMethod fNodeMethod
    ) {
        this.desc = desc;
        this.executorType = executorType;
        this.operation = operation;
        this.fNodeMethod = fNodeMethod;
    }

    /**
     * 严格解析模型动作代码。
     *
     * @param code 动作代码
     * @return 动作枚举
     * @throws IllegalArgumentException 动作未在公共目录定义
     */
    public static FlowActionType fromCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("流程动作不能为空");
        }
        try {
            return valueOf(code);
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("流程包含未定义动作: " + code, failure);
        }
    }

    /**
     * 查找一个 FNode 方法对应的动作目录项。
     *
     * <p>一个底层方法可以有多个业务语义动作，例如坐席外呼和客户外呼都使用
     * {@code FNode.Dial}。该方法返回第一个规范代表项；业务模型应优先使用更具体的动作。</p>
     *
     * @param method FNode 方法对象
     * @return 对应的规范动作
     * @throws IllegalArgumentException 方法没有动作目录项
     */
    public static FlowActionType fromFNodeMethod(FNodeMethod method) {
        if (method == null) {
            throw new IllegalArgumentException("FNode 方法不能为空");
        }
        return Arrays.stream(values())
            .filter(action -> method.equals(action.fNodeMethod))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("FNode 方法没有动作目录项: " + method));
    }
}
