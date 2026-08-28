package com.chandler.learning.agent.security.constant;

/** Auth 业务常量。 */
public final class AuthConstants {

public static final String BEARER_PREFIX = "Bearer ";
        public static final int PASSWORD_MIN_LENGTH = 6;
        public static final int PASSWORD_SALT_BYTES = 16;
        public static final int PASSWORD_HASH_PART_COUNT = 3;
        public static final int PASSWORD_SALT_PART_INDEX = 1;
        public static final int PASSWORD_DIGEST_PART_INDEX = 2;
        public static final String PASSWORD_HASH_SEPARATOR = ":";
        public static final int BEARER_PREFIX_LENGTH = BEARER_PREFIX.length();
        public static final int PHONE_MAX_LENGTH = 32;
        public static final int EMAIL_MAX_LENGTH = 128;
        public static final int PHONE_MASK_THRESHOLD = 7;
        public static final int PHONE_MASK_PREFIX_LENGTH = 3;
        public static final int PHONE_MASK_SUFFIX_LENGTH = 4;
        public static final int EMAIL_MASK_VISIBLE_PREFIX_LENGTH = 2;
        public static final String CONTACT_MASK = "****";

        /**
         * 处理 {@code Auth} 相关业务。
         */
        private AuthConstants() {
        }
}
