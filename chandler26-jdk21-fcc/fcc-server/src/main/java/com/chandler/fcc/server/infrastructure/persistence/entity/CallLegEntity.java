package com.chandler.fcc.server.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

/**
 * 通话话道/Leg 事实持久化实体 (fcc_call_leg)
 * <p>
 * 对应 MySQL 中 fcc_call_leg 表，精确记录每个 FreeSWITCH Channel Leg 的生命周期与时间点。
 * </p>
 *
 * @author Chandler
 */
@TableName("fcc_call_leg")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
@SuperBuilder
public class CallLegEntity extends BaseEntity {

    /**
     * 归属业务通话 ID
     */
    private Long callId;

    /**
     * FreeSWITCH Channel UUID (全局唯一)
     */
    private String channelUuid;

    /**
     * 归属软交换节点标识符 (node_id)
     */
    private String nodeId;

    /**
     * 父级 Leg ID (用于咨询、转接等场景)
     */
    private Long parentLegId;

    /**
     * 话道角色类型 (CUSTOMER, AGENT, IVR, CARRIER)
     */
    private String roleType;

    /**
     * 话道方向 (INBOUND / OUTBOUND)
     */
    private String direction;

    /**
     * 终端接入类型 (SIP, WEBRTC, TRUNK)
     */
    private String endpointType;

    /**
     * 终端账号或标识
     */
    private String endpointId;

    /**
     * 主叫显号
     */
    private String callerNumber;

    /**
     * 被叫号码
     */
    private String destinationNumber;

    /**
     * 关联中继网关 ID
     */
    private Long trunkId;

    /**
     * 关联网关名称
     */
    private String gatewayName;

    /**
     * 话道状态 (START, RINGING, READY, BRIDGE, DESTROY)
     */
    private String state;

    /**
     * 呼叫尝试次序
     */
    private Integer attemptNo;

    /**
     * 话道创建时间 (UTC)
     */
    private LocalDateTime createdTime;

    /**
     * 振铃时间 (UTC)
     */
    private LocalDateTime ringingAt;

    /**
     * 应答接听时间 (UTC)
     */
    private LocalDateTime answeredAt;

    /**
     * 桥接时间 (UTC)
     */
    private LocalDateTime bridgedAt;

    /**
     * 挂机时间 (UTC)
     */
    private LocalDateTime endedAt;

    /**
     * 振铃时长（毫秒）
     */
    private Long ringDurationMs;

    /**
     * 通话时长（毫秒）
     */
    private Long talkDurationMs;

    /**
     * 挂机原因码
     */
    private String hangupCause;

    /**
     * SIP 协议状态码
     */
    private Integer sipStatus;

    /**
     * 扩展元数据 (JSON)
     */
    private String metadata;

    /**
     * 乐观锁版本号
     */
    private Long version;
}
