package com.chandler.fcc.common.dto.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.io.Serializable;

/**
 * Event.Registration SIP 分机注册态生命周期事件数据传输对象
 * <p>
 * 由 Go Sidecar 订阅 FreeSWITCH CUSTOM sofia::register, sofia::unregister, sofia::expire 事件，
 * 清洗后向 NATS 主题（fs.event.{nodeId}.registration）发布，用于精准感知分机在线、下线与租约过期。
 * </p>
 *
 * @author Chandler
 */
@Schema(description = "Event.Registration SIP 分机注册态事件")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class EventRegistrationDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 上报节点标识符
     */
    @JsonProperty("node_id")
    @Schema(description = "FreeSWITCH/Sidecar 节点标识符", example = "fs-node-01")
    private String nodeId;

    /**
     * SIP 分机注册用户名/分机号
     */
    @Schema(description = "分机账号", example = "1007")
    private String user;

    /**
     * SIP 注册域或 Host
     */
    @Schema(description = "SIP 注册域", example = "192.168.18.64")
    private String domain;

    /**
     * 注册状态：REGISTERED(已注册在线), UNREGISTERED(主动注销), EXPIRED(超时离线)
     */
    @Schema(description = "注册状态(REGISTERED/UNREGISTERED/EXPIRED)", example = "REGISTERED")
    private String status;

    /**
     * 终端网络 IP 地址
     */
    @JsonProperty("network_ip")
    @Schema(description = "终端 IP 地址", example = "192.168.18.64")
    private String networkIp;

    /**
     * 终端网络端口号
     */
    @Schema(description = "终端端口号", example = "5060")
    private Integer port;

    /**
     * 终端客户端 User-Agent
     */
    @JsonProperty("user_agent")
    @Schema(description = "终端客户端 User-Agent", example = "Zoiper v5.5 / BoxBox-WebRTC-SDK")
    private String userAgent;

    /**
     * SIP Contact 完整字符串
     */
    @Schema(description = "SIP Contact 标头", example = "sip:1007@192.168.18.64:5060")
    private String contact;

    /**
     * 事件发生时间戳（毫秒）
     */
    @Schema(description = "事件发生时间戳（毫秒）", example = "1789693905000")
    private Long timestamp;
}
