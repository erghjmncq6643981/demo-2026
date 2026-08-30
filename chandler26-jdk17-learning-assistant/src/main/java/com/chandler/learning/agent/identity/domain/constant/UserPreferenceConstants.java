package com.chandler.learning.agent.identity.domain.constant;

/** UserPreference 业务常量。 */
public final class UserPreferenceConstants {

public static final String KEY_LEARNING_AGENT_CODE = "learning.agent_code";
        public static final String KEY_LEARNING_TEMPLATE_CODE = "learning.template_code";
        public static final String KEY_SPEECH_VOICE_TYPE = "speech.voice_type";
        public static final String KEY_SPEECH_SENTENCE_VOICE_NAME = "speech.sentence_voice_name";
        public static final String KEY_SPEECH_SENTENCE_RATE = "speech.sentence_rate";
        public static final String KEY_SPEECH_SENTENCE_PITCH = "speech.sentence_pitch";
        public static final String VOICE_TYPE_US = "us";
        public static final String VOICE_TYPE_UK = "uk";
        public static final double SENTENCE_RATE_DEFAULT = 0.78D;
        public static final double SENTENCE_RATE_MIN = 0.55D;
        public static final double SENTENCE_RATE_MAX = 1.15D;
        public static final double SENTENCE_PITCH_DEFAULT = 1D;
        public static final double SENTENCE_PITCH_MIN = 0.8D;
        public static final double SENTENCE_PITCH_MAX = 1.2D;

        private UserPreferenceConstants() {
        }
}
