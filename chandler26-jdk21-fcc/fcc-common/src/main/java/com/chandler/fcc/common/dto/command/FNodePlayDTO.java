package com.chandler.fcc.common.dto.command;

import com.chandler.fcc.common.protocol.FNodePlayPostAction;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

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

    /**
     * 播放完成后的受控动作。
     */
    @JsonProperty("action_after")
    @Schema(
        description = "播放完成后的动作：NONE(继续当前话务)、PARK(驻留等待下一条指令) 或 HANGUP(正常挂机)",
        example = "PARK"
    )
    private FNodePlayPostAction actionAfter;
}
