package com.chandler.learning.agent.system.domain.constant;

/** SystemLog 业务常量。 */
public final class SystemLogConstants {

public static final int DEFAULT_LIMIT = 80;
        public static final String DEFAULT_LIMIT_PARAM = "80";
        public static final int MIN_LIMIT = 1;
        public static final int MAX_LIMIT = 200;
        public static final String TYPE_SYSTEM = "system";
        public static final String TYPE_AUTH = "auth";
        public static final String TYPE_AI = "ai";
        public static final String TYPE_AI_MODEL = "ai_model";
        public static final String TYPE_CACHE = "cache";
        public static final String TYPE_REVIEW = "review";
        public static final String TYPE_WORDBOOK = "wordbook";
        public static final String TYPE_AGENT = "agent";
        public static final String TYPE_PREFERENCE = "preference";
        public static final String TYPE_VOCABULARY_IMPORT = "vocabulary_import";
        public static final String TYPE_LEARNING_PLAN = "learning_plan";
        public static final String TYPE_ERROR = "error";
        public static final String DEFAULT_TYPE = TYPE_SYSTEM;
        public static final String DEFAULT_TITLE = "系统日志";
        public static final String SOURCE_CLIENT = "client";
        public static final String SOURCE_SERVER = "server";
        public static final int MAX_TYPE_LENGTH = 64;
        public static final int MAX_TITLE_LENGTH = 180;
        public static final int MAX_DETAIL_LENGTH = 8_000;
        public static final int MAX_SOURCE_LENGTH = 32;
        public static final int MAX_BUSINESS_TYPE_LENGTH = 64;
        public static final int MAX_BUSINESS_ID_LENGTH = 128;
        public static final int MAX_TRACE_ID_LENGTH = 64;
        public static final int OUTBOX_BATCH_SIZE = 100;

        private SystemLogConstants() {
        }
}
