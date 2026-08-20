package com.chandler.learning.agent.identity.api;

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
