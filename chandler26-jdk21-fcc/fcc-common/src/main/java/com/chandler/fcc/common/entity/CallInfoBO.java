package com.chandler.fcc.common.entity;

import com.chandler.fcc.common.enums.ActionType;
import com.chandler.fcc.common.enums.CallStageState;
import com.chandler.fcc.common.enums.DirectionType;
import com.chandler.fcc.common.enums.HangupInitiator;
import lombok.*;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * 通话生命周期运行时业务上下文对象 (CallInfo Business Object)
 * <p>
 * 承载单通业务通话在 FCC 控制面运行期间的所有核心属性、状态机进度以及话道映射。
 * 遵循严格标识规范：call_id 为全局通话唯一聚合根；channel_uuid 为话道通道标识；node_id 为软交换节点。
 * </p>
 *
 * @author Chandler
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class CallInfoBO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * FCC 全局业务通话唯一标识符 (call_id)
     */
    private String callId;

    /**
     * 控制流程关联唯一标识符 (ctrl_id)
     */
    private String ctrlId;

    /**
     * 归属的 FreeSWITCH / Go Sidecar 节点标识符 (node_id)
     */
    private String nodeId;

    /**
     * 话务流程模型标识（如 INBOUND_CUSTOMER_SERVICE, OUTBOUND_TWO_WAY_CALL）
     */
    private String modelKey;

    /**
     * 呼叫方向（呼入 / 呼出 / 内部互拨）
     */
    private DirectionType direction;

    /**
     * 当前通话生命周期阶段状态
     */
    private CallStageState stageState;

    /**
     * 主叫电话号码
     */
    private String callerNumber;

    /**
     * 被叫电话号码
     */
    private String destinationNumber;

    /**
     * 当前服务的坐席工号（如 901001 钱丁君）
     */
    private String agentWorkNo;

    /**
     * 坐席注册分机号（如 1007）
     */
    private String agentExt;

    /**
     * 坐席侧 FreeSWITCH Channel UUID
     */
    private String agentChannelUuid;

    /**
     * 客户侧 FreeSWITCH Channel UUID
     */
    private String guestChannelUuid;

    /**
     * 当前正在执行的动作类型
     */
    private ActionType actionType;

    /**
     * 通话总时长（秒）
     */
    private Integer duration;

    /**
     * 通话实际接通计费时长（秒）
     */
    private Integer billsec;

    /**
     * 挂机原因码（如 NORMAL_CLEARING, USER_BUSY）
     */
    private String hangupCause;

    /**
     * 挂机发起方（CALLER 主叫先挂, CALLEE 被叫先挂, SYSTEM 系统拆线, ADMIN 管理员强拆）
     */
    private HangupInitiator hangupInitiator;

    /**
     * 客户服务满意度评分 (1 ~ 5)
     */
    private Integer evaluationScore;

    /**
     * 流程实例上下文变量字典（承载按键值、录音路径、转接暂存参数等）
     */
    @Builder.Default
    private Map<String, Object> data = new HashMap<>();

    /**
     * 获取上下文数据中的字符串值，若不存在则返回默认值
     *
     * @param key          变量键
     * @param defaultValue 默认值
     * @return 变量值字符串
     */
    public String getDataStr(String key, String defaultValue) {
        if (data == null) {
            return defaultValue;
        }
        Object val = data.get(key);
        return val != null ? val.toString() : defaultValue;
    }

    /**
     * 向上下文数据中安全存入变量
     *
     * @param key   变量键
     * @param value 变量值
     */
    public void putData(String key, Object value) {
        if (this.data == null) {
            this.data = new HashMap<>();
        }
        this.data.put(key, value);
    }

    // ================= 兼容性代理方法 =================

    /**
     * 兼容旧版 callUuid 访问（等价于 callId）
     *
     * @return 全局通话唯一标识
     */
    public String getCallUuid() {
        return this.callId;
    }

    /**
     * 兼容旧版 callUuid 设置（等价于 callId）
     *
     * @param callUuid 全局通话唯一标识
     */
    public void setCallUuid(String callUuid) {
        this.callId = callUuid;
    }

    /**
     * 兼容旧版 ctrlUuid 访问（等价于 ctrlId）
     *
     * @return 控制流程关联标识
     */
    public String getCtrlUuid() {
        return this.ctrlId;
    }

    /**
     * 兼容旧版 ctrlUuid 设置（等价于 ctrlId）
     *
     * @param ctrlUuid 控制流程关联标识
     */
    public void setCtrlUuid(String ctrlUuid) {
        this.ctrlId = ctrlUuid;
    }
}
