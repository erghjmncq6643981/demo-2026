package com.chandler.fcc.common.dto.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.io.Serializable;

/**
 * Event.DTMF 按键输入事件数据传输对象
 * <p>
 * 当话道接收到用户按下的电话按键时由 Go Sidecar 清洗上报（主题: fs.event.{nodeId}.dtmf）。
 * </p>
 *
 * @author Chandler
 */
@Schema(description = "Event.DTMF 按键输入事件")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class EventDtmfDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 节点标识符
     */
    @JsonProperty("node_id")
    @Schema(description = "FreeSWITCH/Sidecar 节点标识符", example = "fs-node-01")
    private String nodeId;

    /**
     * 控制流程唯一标识
     */
    @JsonProperty("ctrl_uuid")
    @Schema(description = "控制流程唯一标识", example = "fcc-inbound-1789693905000-abcd1234")
    private String ctrlUuid;

    /**
     * 按键所属话道 UUID
     */
    @Schema(description = "产生按键的话道 UUID", example = "a1b2c3d4-0000-1111-2222-333344445555")
    private String uuid;

    /**
     * 用户按键字符（0-9, *, #）
     */
    @Schema(description = "DTMF 按键字符", example = "1")
    private String digit;

    /**
     * 按键持续毫秒数
     */
    @JsonProperty("duration_ms")
    @Schema(description = "按键持续时长（毫秒）", example = "160")
    private Integer durationMs;

    /**
     * 事件发生时间戳（毫秒）
     */
    @Schema(description = "事件发生时间戳（毫秒）", example = "1789693905000")
    private Long timestamp;
}
