package com.chandler.fcc.admin.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

/**
 * 通话会话聚合根持久化实体 (fcc_call_session)
 * <p>
 * 对应 MySQL 中 fcc_call_session 表，用于话单检索、统计与多条件分页。
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
@SuperBuilder
public class CallSessionEntity extends BaseEntity {

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
     * 流程编码 (如 FLOW-INBOUND)
     */
    private String flowCode;

    /**
     * 呼入路由模式 (DID_DIRECT, RULE_ENGINE, HTTP_CALLBACK)
     */
    private String routeMode;

    /**
     * 目标类型 (AGENT, GROUP)
     */
    private String routeTargetType;

    /**
     * 目标标识 (工号/组ID)
     */
    private String routeTargetId;

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
     * 净通话时长（秒）
     */
    private Integer audioDurationSec;

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
     * 接听坐席工号
     */
    private String agentWorkNo;

    /**
     * 接听坐席姓名
     */
    private String agentName;

    /**
     * 服务满意度评分 (1 ~ 5)
     */
    private Integer evaluationScore;

    /**
     * 录音文件标识或路径
     */
    private String recordFileId;

    /**
     * 扩展属性 JSON (含录音地址 recording_path、DID号、分机号等)
     */
    private String attributes;

    /**
     * 乐观锁版本号
     */
    private Long version;
}
