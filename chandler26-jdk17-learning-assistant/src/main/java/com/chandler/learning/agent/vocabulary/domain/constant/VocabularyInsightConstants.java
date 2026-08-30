package com.chandler.learning.agent.vocabulary.domain.constant;

/** VocabularyInsight 业务常量。 */
public final class VocabularyInsightConstants {

public static final int MAX_RELATIONS = 80;
        public static final int VISIBLE_RELATION_LIMIT = 24;
        public static final int SAME_TAG_LIMIT = 12;
        public static final int TAG_WEIGHT_PART_OF_SPEECH = 90;
        public static final int TAG_WEIGHT_MEANING_TOPIC = 70;
        public static final int TAG_WEIGHT_COLLOCATION = 58;
        public static final int TAG_WEIGHT_WORD_FAMILY = 62;
        public static final int TAG_WEIGHT_DIFFICULTY = 45;
        public static final int RELATION_SCORE_SYNONYM = 92;
        public static final int RELATION_SCORE_ANTONYM = 82;
        public static final int RELATION_SCORE_WORD_FAMILY = 78;
        public static final int RELATION_SCORE_COLLOCATION = 70;
        public static final int RELATION_SCORE_TAG_OVERLAP = 60;
        public static final int HARD_DEFINITION_COUNT = 5;
        public static final int HARD_WORD_LENGTH = 12;
        public static final int MEDIUM_DEFINITION_COUNT = 3;
        public static final int MEDIUM_WORD_LENGTH = 8;
        public static final int TAG_VALUE_MAX_LENGTH = 128;
        public static final int PART_OF_SPEECH_MAX_LENGTH = 50;
        public static final int MEANING_MAX_LENGTH = 512;
        public static final int MATCH_TYPE_MAX_LENGTH = 50;
        public static final String TAG_TYPE_PART_OF_SPEECH = "part_of_speech";
        public static final String TAG_TYPE_MEANING_TOPIC = "meaning_topic";
        public static final String TAG_TYPE_DIFFICULTY = "difficulty";
        public static final String RELATION_TYPE_SYNONYM = "synonym";
        public static final String RELATION_TYPE_ANTONYM = "antonym";
        public static final String RELATION_TYPE_WORD_FAMILY = "word_family";
        public static final String RELATION_TYPE_TAG_OVERLAP = "tag_overlap";
        public static final String RELATION_TYPE_COLLOCATION = "collocation";
        public static final String MATCH_TYPE_PARSED_TEXT = "parsed_text";
        public static final String MATCH_TYPE_PARSED_OBJECT = "parsed_object";
        public static final String MATCH_TYPE_CACHED_EXACT = "cached_exact";
        public static final String MATCH_TYPE_EXACT = "exact";
        public static final String MATCH_TYPE_FUZZY = "fuzzy";
        public static final String DIFFICULTY_EASY = "easy";
        public static final String DIFFICULTY_MEDIUM = "medium";
        public static final String DIFFICULTY_HARD = "hard";
        public static final String SOURCE_PARSED_JSON = "parsed_json";

        private VocabularyInsightConstants() {
        }
}
