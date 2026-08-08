package com.chandler.learning.agent.domain.dto.learning;

import lombok.Data;

/**
 * SpeechPreferenceResponse 类。
 */
@Data
public class SpeechPreferenceResponse {

    private String voiceType;

    private String sentenceVoiceName;

    private Double sentenceRate;

    private Double sentencePitch;
}
