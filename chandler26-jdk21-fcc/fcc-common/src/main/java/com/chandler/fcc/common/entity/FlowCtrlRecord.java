package com.chandler.fcc.common.entity;

import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 流程流转控制审计记录模型
 * <p>
 * 用于对通话生命周期内每一步流转操作执行内存追踪、异步落表及运营审计。
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
public class FlowCtrlRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 流程实例唯一标识符
     */
    private String flowInstanceId;

    /**
     * 业务通话唯一标识符 (call_id)
     */
    private String callId;

    /**
     * 步骤执行唯一标识
     */
    private String stepKey;

    /**
     * 步骤动作类型（如 BRIDGE, RECORD, DIAL）
     */
    private String stepType;

    /**
     * 步骤执行次序号
     */
    private Integer stepOrder;

    /**
     * 所属话务流程模式标识
     */
    private String modelKey;

    /**
     * 步骤执行时的生命周期阶段
     */
    private String stageState;

    /**
     * 审计明细（JSON 或结构化文本）
     */
    private String detail;

    /**
     * 记录产生时间
     */
    private LocalDateTime createTime;

    // ================= 兼容性代理方法 =================

    /**
     * 兼容获取 callUuid
     *
     * @return 业务通话唯一标识符
     */
    public String getCallUuid() {
        return this.callId;
    }

    /**
     * 兼容设置 callUuid
     *
     * @param callUuid 业务通话唯一标识符
     */
    public void setCallUuid(String callUuid) {
        this.callId = callUuid;
    }
}
