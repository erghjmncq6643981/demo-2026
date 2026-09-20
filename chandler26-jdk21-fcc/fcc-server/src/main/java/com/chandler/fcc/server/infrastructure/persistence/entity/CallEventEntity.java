package com.chandler.fcc.server.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 话务原始/清洗事件事实持久化实体 (fcc_call_event)
 * <p>
 * 对应 MySQL 中 fcc_call_event 表，以 Append-only 方式高频记录由 Go Sidecar 清洗后的呼叫事件报文。
 * </p>
 *
 * @author Chandler
 */
@TableName("fcc_call_event")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class CallEventEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 事件雪花主键 ID
     */
    @TableId
    private Long id;

    /**
     * 事件业务唯一标识 (event_id)
     */
    private String eventId;

    /**
     * 软交换节点标识符 (node_id)
     */
    private String nodeId;

    /**
     * 关联业务通话 ID
     */
    private Long callId;

    /**
     * 关联话道 Leg ID
     */
    private Long legId;

    /**
     * FreeSWITCH Channel UUID
     */
    private String channelUuid;

    /**
     * 事件类型 (如 Event.Channel, Event.DTMF, Event.Registration, Event.Recording)
     */
    private String eventType;

    /**
     * 事件单调递增序列号
     */
    private Long eventSequence;

    /**
     * 事件发生时间 (UTC)
     */
    private LocalDateTime eventTime;

    /**
     * 事件接收时间 (UTC)
     */
    private LocalDateTime receivedAt;

    /**
     * 标准化 JSON 载荷
     */
    private String normalizedPayload;

    /**
     * 原始 JSON 载荷
     */
    private String rawPayload;

    /**
     * 处理状态 (RECEIVED, PROCESSED, FAILED)
     */
    private String processStatus;

    /**
     * 异常堆栈或错误描述
     */
    private String processError;
}
