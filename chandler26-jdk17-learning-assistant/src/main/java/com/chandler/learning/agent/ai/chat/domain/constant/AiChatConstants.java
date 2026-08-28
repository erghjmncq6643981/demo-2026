package com.chandler.learning.agent.ai.chat.domain.constant;

/** ChatSession 业务常量。 */
public final class AiChatConstants {

public static final int MAX_HISTORY_SIZE = 20;
        public static final int MAX_HISTORY_CHARS = 24_000;
        public static final int MESSAGE_SEQUENCE_RETRY_COUNT = 3;
        public static final String ROLE_SYSTEM = "system";
        public static final String ROLE_USER = "user";
        public static final String ROLE_ASSISTANT = "assistant";
        public static final String BUSINESS_TYPE_LEARNING = "learning";
        public static final String SCENE_ENGLISH_VOCABULARY = "english_vocabulary";
        public static final String SCENE_ENGLISH_ARTICLE = "english_article";
        public static final String SCENE_ENGLISH_VOCABULARY_PLAN = "english_vocabulary_plan";
        public static final String SCENE_MATH = "math";
        public static final String SCENE_PINYIN = "pinyin";
        public static final String SCENE_WRITING = "writing";
        public static final String SCENE_TITLE_ENGLISH_VOCABULARY = "英语词汇学习";

        /**
         * 处理 {@code ChatSession} 相关业务。
         */
        private AiChatConstants() {
        }
}
