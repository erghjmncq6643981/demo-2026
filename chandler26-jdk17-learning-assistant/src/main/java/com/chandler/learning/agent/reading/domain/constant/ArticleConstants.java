package com.chandler.learning.agent.reading.domain.constant;

/** Article 业务常量。 */
public final class ArticleConstants {

public static final int MIN_SELECTED_WORDS = 1;
        public static final int MAX_SELECTED_WORDS = 20;
        public static final int PRACTICE_QUESTION_COUNT = 3;
        public static final int DEFAULT_HISTORY_LIMIT = 10;
        public static final String DEFAULT_HISTORY_LIMIT_PARAM = "10";
        public static final int MIN_HISTORY_LIMIT = 1;
        public static final int MAX_HISTORY_LIMIT = 50;
        public static final int DEFAULT_LOOKUP_COUNT = 1;
        public static final String STATUS_GENERATED = "generated";
        public static final String STATUS_IN_PROGRESS = "in_progress";
        public static final String STATUS_COMPLETED = "completed";
        public static final String STAGE_READING = "reading";
        public static final String STAGE_VOCABULARY = "vocabulary";
        public static final String STAGE_CHECK = "check";
        public static final String STAGE_COMPLETED = "completed";

        private ArticleConstants() {
        }
}
