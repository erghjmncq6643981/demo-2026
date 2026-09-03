package com.chandler.learning.agent.vocabulary.domain.constant;

/**
 * 词汇发音音频相关常量。
 */
public final class VocabularyAudioConstants {

    private VocabularyAudioConstants() {
    }

    /** 美音标识。 */
    public static final String VOICE_TYPE_US = "us";

    /** 英音标识。 */
    public static final String VOICE_TYPE_UK = "uk";

    /** 默认音频扩展名。 */
    public static final String DEFAULT_AUDIO_EXTENSION = ".mp3";

    /** 有道词典发音地址模板。 */
    public static final String YOUDAO_AUDIO_URL_TEMPLATE = "https://dict.youdao.com/dictvoice?audio=%s&type=%d";

    /** 有道发音类型：英音 = 1。 */
    public static final int YOUDAO_TYPE_UK = 1;

    /** 有道发音类型：美音 = 2。 */
    public static final int YOUDAO_TYPE_US = 2;

    /** 音频下载超时时间（秒）。 */
    public static final int DOWNLOAD_TIMEOUT_SECONDS = 5;

    /** 最小有效音频文件大小（字节），低于此值视为无效响应。 */
    public static final int MIN_VALID_AUDIO_BYTES = 100;
}
