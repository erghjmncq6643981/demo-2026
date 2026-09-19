package com.chandler.fcc.server.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 话务指令发送与审计持久化实体 (fcc_call_command)
 * <p>
 * 对应 MySQL 中 fcc_call_command 表，审计下发给各 FreeSWITCH 软交换节点的 FNode JSON-RPC 控制指令及其幂等键。
 * </p>
 *
 * @author Chandler
 */
@TableName("fcc_call_command")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class CallCommandEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 指令雪花主键 ID
     */
    @TableId
    private Long id;

    /**
     * 租户 ID
     */
    private Long tenantId;

    /**
     * 指令唯一全局标识 (command_id)
     */
    private String commandId;

    /**
     * 指令幂等键 (idempotency_key)
     */
    private String idempotencyKey;

    /**
     * 关联业务通话 ID
     */
    private Long callId;

    /**
     * 关联话道 Leg ID
     */
    private Long legId;

    /**
     * 目标软交换节点标识符 (target_node_id)
     */
    private String targetNodeId;

    /**
     * 调用的 RPC 方法名 (如 FNode.Dial, FNode.Bridge, FNode.Play)
     */
    private String methodName;

    /**
     * 指令请求载荷 (JSON)
     */
    private String requestPayload;

    /**
     * 指令响应载荷 (JSON)
     */
    private String responsePayload;

    /**
     * 指令执行状态 (CREATED, SENT, SUCCESS, FAILED, TIMEOUT)
     */
    private String status;

    /**
     * 错误代码
     */
    private String errorCode;

    /**
     * 错误详细描述
     */
    private String errorMessage;

    /**
     * 重试次数
     */
    private Integer retryCount;

    /**
     * 创建时间 (UTC)
     */
    private LocalDateTime createdAt;

    /**
     * 发送时间 (UTC)
     */
    private LocalDateTime sentAt;

    /**
     * 完成响应时间 (UTC)
     */
    private LocalDateTime completedAt;
}
