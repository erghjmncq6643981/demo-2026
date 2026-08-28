package com.chandler.learning.agent.vocabulary.domain.constant;

/** VocabularyCard 业务常量。 */
public final class VocabularyCardConstants {

public static final String STATUS_MISSING = "missing";
        public static final String STATUS_QUEUED = "queued";
        public static final String STATUS_GENERATING = "generating";
        public static final String STATUS_READY = "ready";
        public static final String STATUS_FAILED = "failed";
        public static final String STATUS_NOT_REQUIRED = "not_required";
        public static final String JOB_PENDING = "pending";
        public static final String JOB_RUNNING = "running";
        public static final String JOB_COMPLETED = "completed";
        public static final String JOB_PARTIAL_FAILED = "partial_failed";
        public static final String JOB_FAILED = "failed";
        public static final String JOB_CANCELLED = "cancelled";
        public static final String ITEM_PENDING = "pending";
        public static final String ITEM_GENERATING = "generating";
        public static final String ITEM_COMPLETED = "completed";
        public static final String ITEM_FAILED = "failed";
        public static final String ITEM_CACHE_HIT = "cache_hit";
        public static final int DEFAULT_BATCH_SIZE = 15;
        public static final int MIN_BATCH_SIZE = 10;
        public static final int MAX_BATCH_SIZE = 20;
        public static final int DEFAULT_ITEM_PAGE_SIZE = 100;
        public static final int MAX_ITEM_PAGE_SIZE = 200;

        private VocabularyCardConstants() {
        }
}
