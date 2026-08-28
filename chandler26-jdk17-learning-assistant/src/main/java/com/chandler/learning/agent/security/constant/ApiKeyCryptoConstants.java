package com.chandler.learning.agent.security.constant;

/** Crypto 业务常量。 */
public final class ApiKeyCryptoConstants {

public static final String API_KEY_PREFIX = "enc:v1:";
        public static final String API_KEY_CIPHER = "AES/GCM/NoPadding";
        public static final int API_KEY_IV_LENGTH = 12;
        public static final int API_KEY_TAG_BITS = 128;
        public static final int API_KEY_CIPHER_PART_COUNT = 2;
        public static final int API_KEY_MASK_THRESHOLD = 10;
        public static final int API_KEY_MASK_PREFIX_LENGTH = 6;
        public static final int API_KEY_MASK_SUFFIX_LENGTH = 4;
        public static final int API_KEY_FINGERPRINT_LENGTH = 16;

        /**
         * 处理 {@code Crypto} 相关业务。
         */
        private ApiKeyCryptoConstants() {
        }
}
