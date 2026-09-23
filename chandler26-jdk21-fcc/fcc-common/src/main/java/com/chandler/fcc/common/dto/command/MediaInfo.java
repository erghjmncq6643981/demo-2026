package com.chandler.fcc.common.dto.command;

import com.chandler.fcc.common.protocol.FNodeMediaType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

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
    private FNodeMediaType type;

    /**
     * 播报载荷：TTS 文本内容或本地音频文件路径
     */
    @Schema(description = "TTS 文本内容或本地音频文件路径", example = "ivr/welcome_prompt.wav")
    private String data;

}
