package com.chandler.learning.agent.security.constant;

/** Jwt 业务常量。 */
public final class JwtConstants {

    public static final int DEFAULT_EXPIRE_DAYS = 30;

public static final int TOKEN_PART_COUNT = 3;
        public static final int SIGNATURE_PART_INDEX = 2;
        public static final long SECONDS_PER_DAY = 86_400L;

        /**
         * 处理 {@code Jwt} 相关业务。
         */
        private JwtConstants() {
        }
}
