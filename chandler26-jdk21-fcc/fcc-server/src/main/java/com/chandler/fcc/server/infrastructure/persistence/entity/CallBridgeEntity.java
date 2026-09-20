package com.chandler.fcc.server.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 通话媒体桥接事实持久化实体 (fcc_call_bridge)
 * <p>
 * 对应 MySQL 中 fcc_call_bridge 表，维护通话话道间创建的媒体桥接生命周期。
 * </p>
 *
 * @author Chandler
 */
@TableName("fcc_call_bridge")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class CallBridgeEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 桥接雪花主键 ID
     */
    @TableId
    private Long id;

    /**
     * 关联业务通话 ID
     */
    private Long callId;

    /**
     * 桥接 UUID 标识
     */
    private String bridgeUuid;

    /**
     * 桥接类型 (DIRECT, THREE_WAY, CONFERENCE)
     */
    private String bridgeType;

    /**
     * 桥接建立时间 (UTC)
     */
    private LocalDateTime startedAt;

    /**
     * 桥接拆除时间 (UTC)
     */
    private LocalDateTime endedAt;

    /**
     * 记录创建时间 (UTC)
     */
    private LocalDateTime createdAt;
}
