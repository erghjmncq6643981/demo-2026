package com.chandler.fcc.common.dto.command;

import com.chandler.fcc.common.protocol.FNodeDtmfPostAction;
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
 * FNode.ReadDTMF 按键收号指令入参模型
 * <p>
 * 用于下发通道播放提示语音并收集用户输入的 DTMF 电话按键（如 IVR 菜单选择或服务满意度评价）。
 * </p>
 *
 * @author Chandler
 */
@Schema(description = "FNode.ReadDTMF 按键收号指令入参")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class FNodeReadDTMFDTO implements Serializable {

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
    @Schema(description = "收号目标话道 UUID", example = "a1b2c3d4-0000-1111-2222-333344445555")
    private String uuid;

    /**
     * 引导提示音媒体信息
     */
    @Schema(description = "引导提示媒体（音频或TTS文本）")
    private MediaInfo media;

    /**
     * 最少按键位数
     */
    @JsonProperty("min_digits")
    @Schema(description = "最少接收按键位数", example = "1")
    private Integer minDigits;

    /**
     * 最多按键位数
     */
    @JsonProperty("max_digits")
    @Schema(description = "最多接收按键位数", example = "1")
    private Integer maxDigits;

    /**
     * 引导音最大重试次数
     */
    @Schema(description = "未按键时的最大重试引导次数", example = "2")
    private Integer tries;

    /**
     * 首位按键等待超时时间（毫秒）
    */
    @Schema(description = "等待用户首按键超时时间（毫秒）", example = "5000")
    private Integer timeout;

    /**
     * 连续按键之间的间隔超时时间（毫秒）
     */
    @JsonProperty("digit_timeout")
    @Schema(description = "按键之间的间隔超时时间（毫秒）", example = "2000")
    private Integer digitTimeout;

    /**
     * 结束按键字符集合（如 "#"）
     */
    @Schema(description = "确认结束字符", example = "#")
    private String terminators;

    /**
     * 收号完成后的感谢音文件
     */
    @JsonProperty("thank_you_file")
    @Schema(description = "收号完成后的感谢音音频文件路径", example = "ivr/thank_you.wav")
    private String thankYouFile;

    /**
     * 按键校验正则表达式
     */
    @Schema(description = "有效按键正则表达式（如 [1-5]）", example = "^[1-5]$")
    private String regex;

    /**
     * 收号完成后的后置行为
     */
    @JsonProperty("action_after")
    @Schema(description = "收号完成后的行为（如 HANGUP）", example = "HANGUP")
    private FNodeDtmfPostAction actionAfter;
}
