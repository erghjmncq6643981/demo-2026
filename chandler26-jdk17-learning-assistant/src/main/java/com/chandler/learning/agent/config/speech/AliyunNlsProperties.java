package com.chandler.learning.agent.config.speech;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 阿里云智能语音交互（NLS）配置属性。
 */
@Data
@Component
@ConfigurationProperties(prefix = "learning.aliyun.nls")
public class AliyunNlsProperties {

    /** 项目 AppKey。 */
    private String appKey;

    /** AccessKey ID。 */
    private String akId;

    /** AccessKey Secret。 */
    private String akSecret;

    /** NLS 网关 WebSocket 地址。 */
    private String gatewayUrl = "wss://nls-gateway-cn-shanghai.aliyuncs.com/ws/v1";

    /** 默认发音人音色（如 siyue、zhimiao_emo 等）。 */
    private String voice = "siyue";

    /** 默认语速（-500 到 500，默认 -120 约 0.88x 适合精读与跟读）。 */
    private Integer speechRate = -120;

    /** 音频采样率。 */
    private Integer sampleRate = 16000;

    /** 单次 TTS 转换的最大字符限制（默认 200 字以内，避免超限）。 */
    private Integer maxChunkLength = 200;
}
