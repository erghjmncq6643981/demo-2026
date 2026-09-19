package com.chandler.fcc.common.dto.command;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.io.Serializable;

/**
 * 媒体播报参数模型
 * <p>
 * 封装放音播报或收号提示音的媒体类型（TTS 文本或本地音频文件路径）及引擎发音人设置。
 * </p>
 *
 * @author Chandler
 */
@Schema(description = "媒体播报参数对象")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class MediaInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 媒体类型
     */
    @Schema(description = "媒体类型：TEXT(动态语音合成) 或 FILE(音频文件)", example = "FILE")
    private String type;

    /**
     * 播报载荷：TTS 文本内容或本地音频文件路径
     */
    @Schema(description = "TTS 文本内容或本地音频文件路径", example = "ivr/welcome_prompt.wav")
    private String data;

    /**
     * TTS 发音人声音标识
     */
    @Schema(description = "TTS 发音人声音标识（如 aiqi, xiaoyun）", example = "aiqi")
    private String voice;

    /**
     * 语音合成引擎
     */
    @Schema(description = "语音合成引擎（如 ali, tencent, flite）", example = "ali")
    private String engine;
}
