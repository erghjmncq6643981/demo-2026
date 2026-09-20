package com.chandler.fcc.common.dto.command;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.io.Serializable;

/**
 * FNode.Record 通道录音指令入参模型
 * <p>
 * 用于启动或停止 FreeSWITCH 话道的双轨立体声录音落盘。
 * </p>
 *
 * @author Chandler
 */
@Schema(description = "FNode.Record 通道录音指令入参")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class FNodeRecordDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 控制流程唯一标识
     */
    @JsonProperty("ctrl_uuid")
    @Schema(description = "控制流程唯一标识", example = "fcc-inbound-1789693905000-abcd1234")
    private String ctrlUuid;

    /**
     * 录音目标通道 UUID
     */
    @Schema(description = "录音话道 UUID", example = "a1b2c3d4-0000-1111-2222-333344445555")
    private String uuid;

    /**
     * 录音动作类型
     */
    @Schema(description = "录音动作指令：START(开始录音) 或 STOP(停止录音)", example = "START")
    private String action;

    /**
     * 录音文件落盘完整路径
     */
    @Schema(description = "录音存储绝对路径（WAV 或 MP3 格式），由控制面按共享目录布局声明", example = "/Users/chandler/fcc-records/2026/09/19/1789693905.wav")
    private String path;
}
