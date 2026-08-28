package com.chandler.learning.agent.learning.domain.constant;

/** ScenePlan 业务常量。 */
public final class ScenePlanConstants {

public static final String STATUS_NOT_STARTED = "not_started";
        public static final String STATUS_ACTIVE = "active";
        public static final String STATUS_COMPLETED = "completed";
        public static final String STATUS_PAUSED = "paused";
        public static final String STATUS_CANCELLED = "cancelled";
        public static final String UNIT_READY = "ready";
        public static final String UNIT_IN_PROGRESS = "in_progress";
        public static final String UNIT_COMPLETED = "completed";
        public static final String TIER_CORE = "core";
        public static final String TIER_EXTENDED = "extended";
        public static final String TIER_SUPPLEMENTARY = "supplementary";
        public static final String TIER_REVIEW = "review";
        public static final String MASTERY_RECOGNITION = "recognition";
        public static final String MASTERY_SPELLING = "spelling";
        public static final String PROGRESS_UNSEEN = "unseen";
        public static final String PROGRESS_EXPOSED = "exposed";
        public static final String PROGRESS_LEARNING = "learning";
        public static final String PROGRESS_REVIEWING = "reviewing";
        public static final String PROGRESS_MASTERED = "mastered";
        public static final String ASSESSMENT_MEANING_CHOICE = "meaning_choice";
        public static final String ASSESSMENT_COPY_TYPING = "copy_typing";
        public static final String ASSESSMENT_MEANING_SPELLING = "meaning_spelling";
        public static final String CHECK_CORRECT = "correct";
        public static final String CHECK_INCORRECT = "incorrect";
        public static final int MIN_CORE_WORDS = 8;
        public static final int MAX_CORE_WORDS_PER_UNIT = 50;
        public static final int MAX_REVIEW_WORDS = 20;
        public static final int PREFERRED_GROUP_SCORE = 1000;
        public static final int SUB_TOPIC_SCORE = 5;
        public static final int RECOGNITION_PASS_SCORE = 70;
        public static final int SPELLING_PASS_SCORE = 70;
        public static final int GENERATION_LOCK_MINUTES = 5;

        private ScenePlanConstants() {
        }
}
