package com.chandler.fcc.common.dto.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.io.Serializable;
import java.util.Map;

/**
 * Event.Channel 通道生命周期事件数据传输对象
 * <p>
 * 由 Go Sidecar 对 FreeSWITCH Inbound ESL 上报的 CHANNEL_CREATE, CHANNEL_PROGRESS,
 * CHANNEL_ANSWER, CHANNEL_PARK, CHANNEL_BRIDGE, CHANNEL_HANGUP, CHANNEL_DESTROY
 * 进行标准化清洗后发布至 NATS（主题: fs.event.{nodeId}.channel）。
 * </p>
 *
 * @author Chandler
 */
@Schema(description = "Event.Channel 通道状态流转事件")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class EventChannelDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 上报节点标识符
     */
    @JsonProperty("node_id")
    @Schema(description = "FreeSWITCH/Sidecar 节点标识符", example = "fs-node-01")
    private String nodeId;

    /**
     * 规范化后的通道状态 (START, CALLING, RINGING, READY, BRIDGE, DESTROY)
     */
    @Schema(description = "标准化通道状态", example = "READY")
    private String state;

    /**
     * 当前话道 Channel UUID
     */
    @Schema(description = "当前话道 UUID", example = "a1b2c3d4-0000-1111-2222-333344445555")
    private String uuid;

    /**
     * 对端话道 Peer UUID (若已桥接)
     */
    @JsonProperty("peer_uuid")
    @Schema(description = "对端话道 UUID", example = "b2c3d4e5-0000-1111-2222-333344445555")
    private String peerUuid;

    /**
     * 控制流程唯一标识
     */
    @JsonProperty("ctrl_uuid")
    @Schema(description = "控制流程唯一标识", example = "fcc-inbound-1789693905000-abcd1234")
    private String ctrlUuid;

    /**
     * 呼叫方向 (inbound / outbound)
     */
    @Schema(description = "呼叫方向", example = "inbound")
    private String direction;

    /**
     * 主叫显号名称
     */
    @JsonProperty("cid_name")
    @Schema(description = "主叫显号名称", example = "1008")
    private String cidName;

    /**
     * 主叫电话号码
     */
    @JsonProperty("cid_number")
    @Schema(description = "主叫电话号码", example = "1008")
    private String cidNumber;

    /**
     * 被叫电话号码
     */
    @JsonProperty("dest_number")
    @Schema(description = "被叫电话号码", example = "9000")
    private String destNumber;

    /**
     * 通话总时长（秒）
     */
    @Schema(description = "通话总时长（秒）", example = "45")
    private Integer duration;

    /**
     * 计费应答时长（秒）
     */
    @Schema(description = "计费通话时长（秒）", example = "38")
    private Integer billsec;

    /**
     * 挂机原因码
     */
    @Schema(description = "挂机原因码", example = "NORMAL_CLEARING")
    private String cause;

    /**
     * SIP 协议响应状态码
     */
    @JsonProperty("sip_status")
    @Schema(description = "SIP 状态码", example = "200")
    private Integer sipStatus;

    /**
     * 事件发生时间戳（毫秒）
     */
    @Schema(description = "事件发生时间戳（毫秒）", example = "1789693905000")
    private Long timestamp;

    /**
     * 扩展通道变量
     */
    @Schema(description = "扩展通道变量字典")
    private Map<String, String> params;
}
