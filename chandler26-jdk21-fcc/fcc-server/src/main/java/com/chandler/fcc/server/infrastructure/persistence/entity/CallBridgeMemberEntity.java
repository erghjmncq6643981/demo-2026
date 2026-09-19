package com.chandler.fcc.server.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 桥接成员话道关联持久化实体 (fcc_call_bridge_member)
 * <p>
 * 对应 MySQL 中 fcc_call_bridge_member 表，记录参与媒体桥接的各话道 Leg 及其加入、退出时间点。
 * </p>
 *
 * @author Chandler
 */
@TableName("fcc_call_bridge_member")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class CallBridgeMemberEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 成员雪花主键 ID
     */
    @TableId
    private Long id;

    /**
     * 关联桥接 ID
     */
    private Long bridgeId;

    /**
     * 关联参与话道 Leg ID
     */
    private Long legId;

    /**
     * 加入桥接时间 (UTC)
     */
    private LocalDateTime joinedAt;

    /**
     * 离开桥接时间 (UTC)
     */
    private LocalDateTime leftAt;

    /**
     * 记录创建时间 (UTC)
     */
    private LocalDateTime createdAt;
}
