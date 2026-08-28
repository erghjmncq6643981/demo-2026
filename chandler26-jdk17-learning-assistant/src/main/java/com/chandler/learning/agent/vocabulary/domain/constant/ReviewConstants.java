package com.chandler.learning.agent.vocabulary.domain.constant;

/** Review 业务常量。 */
public final class ReviewConstants {

public static final String STATUS_FAMILIAR = "familiar";
        public static final String STATUS_FORGOTTEN = "forgotten";
        public static final String STATUS_VAGUE = "vague";
        public static final String RESULT_REMEMBERED = "remembered";
        public static final String RESULT_VAGUE = "vague";
        public static final String RESULT_FORGOTTEN = "forgotten";
        public static final int INITIAL_STAGE = 0;
        public static final int INITIAL_MASTERY = 0;
        public static final int MIN_MASTERY = 0;
        public static final int MAX_MASTERY = 100;
        public static final int FAMILIAR_MASTERY_THRESHOLD = 70;
        public static final int REMEMBERED_MASTERY_DELTA = 15;
        public static final int VAGUE_MASTERY_DELTA = 5;
        public static final int FORGOTTEN_MASTERY_DELTA = 20;
        public static final int VAGUE_REVIEW_DELAY_DAYS = 1;
        public static final int FORGOTTEN_REVIEW_DELAY_HOURS = 4;
        public static final int SLEEP_START_HOUR = 0;
        public static final int SLEEP_END_HOUR = 6;
        public static final int DAY_END_HOUR = 24;
        public static final int DUE_DEFAULT_LIMIT = 10;
        public static final String DUE_DEFAULT_LIMIT_PARAM = "10";
        public static final int DUE_MIN_LIMIT = 1;
        public static final int DUE_MAX_LIMIT = 100;
        public static final int RESTART_DEFAULT_LIMIT = 10;
        public static final String RESTART_DEFAULT_LIMIT_PARAM = "10";
        public static final int RESTART_MIN_LIMIT = 1;
        public static final int RESTART_MAX_LIMIT = 100;
        public static final int[] INTERVAL_DAYS = {0, 1, 2, 4, 7, 15, 30, 60};

        /**
         * 处理 {@code Review} 相关业务。
         */
        private ReviewConstants() {
        }
}
