package com.chandler.learning.agent.domain.dto.learning;

import lombok.Data;

/**
 * SpeechPreferenceRequest 类。
 */
@Data
public class SpeechPreferenceRequest {

    private String voiceType;

    private String sentenceVoiceName;

    private Double sentenceRate;

    private Double sentencePitch;
}
