package com.chandler.learning.agent.vocabulary.domain.constant;

/** Vocabulary 业务常量。 */
public final class VocabularyConstants {

public static final int DEFAULT_LOOKUP_COUNT = 1;
        public static final int EXACT_MATCH_SCORE = 100;
        public static final int FUZZY_MATCH_CANDIDATE_LIMIT = 1_000;
        public static final int FUZZY_MATCH_MIN_SCORE = 45;
        public static final int FUZZY_MATCH_MAX_SCORE = 99;
        public static final int MIN_MATCH_SCORE = 0;
        public static final int PREFIX_SCORE_BOOST = 12;
        public static final int SAME_INITIAL_SCORE_BOOST = 6;
        public static final int COMMON_PREFIX_MIN_LENGTH = 2;
        public static final int COMMON_PREFIX_SCORE_BOOST = 8;
        public static final int CONTAINS_SCORE_BOOST = 8;
        public static final int EDIT_DISTANCE_INSERT_DELETE_COST = 1;
        public static final int EDIT_DISTANCE_SAME_COST = 0;
        public static final int EDIT_DISTANCE_REPLACE_COST = 1;

        private VocabularyConstants() {
        }
}
