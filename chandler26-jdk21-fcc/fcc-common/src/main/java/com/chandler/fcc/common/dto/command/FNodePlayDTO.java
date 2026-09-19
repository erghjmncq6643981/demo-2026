package com.chandler.fcc.common.dto.command;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.io.Serializable;

/**
 * FNode.Play 放音播报指令入参模型
 * <p>
 * 用于在指定话道通道上播放本地录音文件或执行动态 TTS 文本语音播报。
 * </p>
 *
 * @author Chandler
 */
@Schema(description = "FNode.Play 放音播报指令入参")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class FNodePlayDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 控制流程唯一标识
     */
    @JsonProperty("ctrl_uuid")
    @Schema(description = "控制流程唯一标识", example = "fcc-inbound-1789693905000-abcd1234")
    private String ctrlUuid;

    /**
     * 目标通道 UUID
     */
    @Schema(description = "放音目标话道 UUID", example = "a1b2c3d4-0000-1111-2222-333344445555")
    private String uuid;

    /**
     * 播报媒体详情
     */
    @Schema(description = "播报媒体详细参数")
    private MediaInfo media;
}
