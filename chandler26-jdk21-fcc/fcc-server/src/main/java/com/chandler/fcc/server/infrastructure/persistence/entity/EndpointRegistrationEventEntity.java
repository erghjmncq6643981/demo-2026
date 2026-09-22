package com.chandler.fcc.server.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * SIP/WebRTC 终端注册状态追加事实。
 */
@TableName("fcc_endpoint_registration_event")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EndpointRegistrationEventEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 注册事件主键。 */
    @TableId
    private Long id;

    /** 可选的分机资源主键。 */
    private Long extensionId;

    /** 注册分机号。 */
    private String extension;

    /** 注册协议。 */
    private String protocol;

    /** FreeSWITCH 注册状态。 */
    private String status;

    /** SIP Contact。 */
    private String contact;

    /** 终端 User-Agent。 */
    private String userAgent;

    /** 终端网络地址。 */
    private String networkIp;

    /** 终端网络端口。 */
    private Integer networkPort;

    /** 处理该注册的基础设施节点。 */
    private String nodeId;

    /** 源事件发生时间。 */
    private LocalDateTime occurredAt;

    /** 事实写入时间。 */
    private LocalDateTime createdAt;
}
