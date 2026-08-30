package com.chandler.learning.agent.identity.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Data;

/**
 * 当前业务响应数据。
 */
@Data
public class SpeechPreferenceResponse {

    @Schema(description = "发音类型")
    private String voiceType;

    @Schema(description = "句子发音人")
    private String sentenceVoiceName;

    @Schema(description = "句子朗读语速")
    private Double sentenceRate;

    @Schema(description = "句子朗读音调")
    private Double sentencePitch;
}
