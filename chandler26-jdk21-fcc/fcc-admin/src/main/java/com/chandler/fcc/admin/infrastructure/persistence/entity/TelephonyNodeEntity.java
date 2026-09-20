package com.chandler.fcc.admin.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

/**
 * 通信节点 (FreeSWITCH / Sidecar) 注册实体 (fcc_telephony_node)
 * <p>
 * 对应 MySQL 中 fcc_telephony_node 表，记录通信网关节点运行状态、并发负载与健康心跳。
 * </p>
 *
 * @author Chandler
 */
@TableName("fcc_telephony_node")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
@SuperBuilder
public class TelephonyNodeEntity extends BaseEntity {

    /**
     * 节点唯一代码 (如 node-fs-01)
     */
    private String nodeId;

    /**
     * 节点所属集群标识 (默认 default)
     */
    private String clusterId;

    /**
     * 节点通信主机/IP 地址
     */
    private String host;

    /**
     * 可用区 (如 zone-a)
     */
    private String zone;

    /**
     * 节点状态 (ONLINE, OFFLINE, DRAINING)
     */
    private String status;

    /**
     * 节点支持的最大并发路数
     */
    private Integer maxChannels;

    /**
     * 当前活跃通道并发数
     */
    private Integer activeChannels;

    /**
     * 优雅下线/排空起始时间
     */
    private LocalDateTime drainingSince;

    /**
     * 最近一次心跳上报时间
     */
    private LocalDateTime lastHeartbeatAt;

    /**
     * 扩展元数据 (JSON)
     */
    private String metadata;
}
