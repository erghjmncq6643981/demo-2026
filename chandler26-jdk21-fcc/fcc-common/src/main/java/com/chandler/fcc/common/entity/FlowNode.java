package com.chandler.fcc.common.entity;

import com.chandler.fcc.common.enums.ActionType;
import com.chandler.fcc.common.enums.CallStageState;
import com.chandler.fcc.common.enums.FlowModelType;
import lombok.*;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * 流程编排执行节点定义模型
 * <p>
 * 描述呼叫流转引擎中每一个原子步骤的动作类型、执行顺序、状态阶段以及动作入参。
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
public class FlowNode implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 所属话务流程模式标识（如 INBOUND_CUSTOMER_SERVICE）
     */
    private String modelKey;

    /**
     * 话务流程模式枚举
     */
    private FlowModelType modelType;

    /**
     * 触发该节点的呼叫生命周期阶段状态
     */
    private CallStageState stageState;

    /**
     * 节点动作唯一键（如 dial-guest, bridge-agent-guest）
     */
    private String actionKey;

    /**
     * 同一阶段内的步骤执行顺序号（从 1 开始单调递增）
     */
    private Integer order;

    /**
     * 动作类型（如 CHANNEL_BRIDGE, RECORD, PLAY 等）
     */
    private ActionType actionType;

    /**
     * 节点执行专属参数字典（如播放文件名、收号位数、超时时间等）
     */
    @Builder.Default
    private Map<String, Object> data = new HashMap<>();

    /**
     * 向节点参数字典中安全存入变量
     *
     * @param key   参数键
     * @param value 参数值
     * @return 当前 FlowNode 实例（支持链式调用）
     */
    public FlowNode addData(String key, Object value) {
        if (this.data == null) {
            this.data = new HashMap<>();
        }
        this.data.put(key, value);
        return this;
    }

    /**
     * 获取参数字典中的字符串值
     *
     * @param key          参数键
     * @param defaultValue 默认值
     * @return 参数字符串
     */
    public String getDataStr(String key, String defaultValue) {
        if (this.data == null) {
            return defaultValue;
        }
        Object val = this.data.get(key);
        return val != null ? val.toString() : defaultValue;
    }
}
