package com.chandler.learning.agent.vocabulary.domain.constant;

/** VocabularyImport 业务常量。 */
public final class VocabularyImportConstants {

public static final String FORMAT_MARKDOWN = "markdown";
        public static final String STATUS_PARSING = "parsing";
        public static final String STATUS_REVIEWING = "reviewing";
        public static final String STATUS_PUBLISHED = "published";
        public static final String STATUS_FAILED = "failed";
        public static final String VERSION_STATUS_REVIEWING = "reviewing";
        public static final String VERSION_STATUS_PUBLISHED = "published";
        public static final String CATALOG_STATUS_DRAFT = "draft";
        public static final String CATALOG_STATUS_PUBLISHED = "published";
        public static final String VISIBILITY_PRIVATE = "private";
        public static final String VISIBILITY_PUBLIC = "public";
        public static final String SOURCE_SELF_STUDY = "self_study";
        public static final String SOURCE_CET4 = "cet4";
        public static final String SOURCE_CET6 = "cet6";
        public static final String SOURCE_IELTS = "ielts";
        public static final String REVIEW_NOT_REQUIRED = "not_required";
        public static final String REVIEW_PENDING = "pending";
        public static final String REVIEW_CONFIRMED = "confirmed";
        public static final String WARNING_SUSPICIOUS_SPLIT = "suspicious_split";
        public static final int DEFAULT_PAGE = 1;
        public static final int DEFAULT_PAGE_SIZE = 100;
        public static final int MAX_PAGE_SIZE = 500;

        private VocabularyImportConstants() {
        }
}
