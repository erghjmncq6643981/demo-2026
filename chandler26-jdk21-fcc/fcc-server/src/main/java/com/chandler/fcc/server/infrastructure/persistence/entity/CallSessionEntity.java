package com.chandler.fcc.server.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 通话会话聚合根持久化实体 (fcc_call_session)
 * <p>
 * 对应 MySQL 中 fcc_call_session 表，保存全局业务通话的事实终态与审计。
 * </p>
 *
 * @author Chandler
 */
@TableName("fcc_call_session")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class CallSessionEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 通话雪花主键 ID
     */
    @TableId
    private Long id;

    /**
     * 租户 ID
     */
    private Long tenantId;

    /**
     * 外部业务单据号
     */
    private String bizId;

    /**
     * 控制流程唯一关联标识 (ctrl_id)
     */
    private String ctrlId;

    /**
     * 话务流转模式标识 (如 INBOUND_CUSTOMER_SERVICE)
     */
    private String modelType;

    /**
     * 呼叫方向 (INBOUND / OUTBOUND / INTERNAL)
     */
    private String direction;

    /**
     * 主叫电话号码
     */
    private String callerNumber;

    /**
     * 被叫电话号码
     */
    private String destinationNumber;

    /**
     * 通话状态 (如 START, CONNECTED, NORMAL_END)
     */
    private String status;

    /**
     * 通话结果 (如 ANSWERED, NO_ANSWER, BUSY)
     */
    private String result;

    /**
     * 通话开始时间 (UTC)
     */
    private LocalDateTime startedAt;

    /**
     * 振铃开始时间 (UTC)
     */
    private LocalDateTime ringingAt;

    /**
     * 双方接通时间 (UTC)
     */
    private LocalDateTime answeredAt;

    /**
     * 通话结束时间 (UTC)
     */
    private LocalDateTime endedAt;

    /**
     * 振铃时长（毫秒）
     */
    private Long ringDurationMs;

    /**
     * 实际通话时长（毫秒）
     */
    private Long talkDurationMs;

    /**
     * 总时长（毫秒）
     */
    private Long totalDurationMs;

    /**
     * 挂机原因码
     */
    private String hangupCause;

    /**
     * 挂机发起方 (CALLER / CALLEE / SYSTEM / ADMIN)
     */
    private String hangupInitiator;

    /**
     * 主服务坐席 ID
     */
    private Long primaryAgentId;

    /**
     * 主服务坐席工号 (如 901001)
     */
    private String primaryWorkNo;

    /**
     * 实际接待坐席工号 (用于前序通话"防撞单"检索)
     */
    private String agentWorkNo;

    /**
     * 实际接待坐席姓名 (用于前序通话"防撞单"检索)
     */
    private String agentName;

    /**
     * 服务满意度评分 (1 ~ 5)
     */
    private Integer evaluationScore;

    /**
     * 扩展变量属性 (JSON 格式)
     */
    private String attributes;

    /**
     * 乐观锁版本号
     */
    private Long version;

    /**
     * 记录创建时间 (UTC)
     */
    private LocalDateTime createdAt;

    /**
     * 记录更新时间 (UTC)
     */
    private LocalDateTime updatedAt;
}
