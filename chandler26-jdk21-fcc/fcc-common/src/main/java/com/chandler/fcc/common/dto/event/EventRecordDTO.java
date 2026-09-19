package com.chandler.fcc.common.dto.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.io.Serializable;

/**
 * Event.Recording 录音生命周期与完成事件数据传输对象
 * <p>
 * 录音开始或停止录音文件落盘完成后，由 Go Sidecar 清洗上报（主题: fs.event.{nodeId}.recording）。
 * </p>
 *
 * @author Chandler
 */
@Schema(description = "Event.Recording 录音完成事件")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class EventRecordDTO implements Serializable {

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
     * 录音所属通道 UUID
     */
    @Schema(description = "通道唯一 UUID", example = "a1b2c3d4-0000-1111-2222-333344445555")
    private String uuid;

    /**
     * 录音动作类型 (START / STOP)
     */
    @Schema(description = "录音动作类型", example = "STOP")
    private String action;

    /**
     * 录音文件绝对物理路径
     */
    @Schema(description = "录音文件物理路径", example = "/Users/chandler/fcc-records/2026/09/19/call-1789693905.wav")
    private String path;

    /**
     * 录音持续时长（毫秒）
     */
    @JsonProperty("duration_ms")
    @Schema(description = "录音持续毫秒数", example = "45000")
    private Long durationMs;

    /**
     * 录音文件大小（字节）
     */
    @JsonProperty("size_bytes")
    @Schema(description = "录音文件大小（字节）", example = "720000")
    private Long sizeBytes;

    /**
     * 事件时间戳（毫秒）
     */
    @Schema(description = "事件时间戳（毫秒）", example = "1789693905000")
    private Long timestamp;
}
