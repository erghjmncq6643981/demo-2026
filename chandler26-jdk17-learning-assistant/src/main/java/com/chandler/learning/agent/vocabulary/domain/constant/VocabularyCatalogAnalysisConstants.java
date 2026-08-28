package com.chandler.learning.agent.vocabulary.domain.constant;

/** VocabularyAnalysis 业务常量。 */
public final class VocabularyCatalogAnalysisConstants {

public static final String STATUS_PENDING = "pending";
        public static final String STATUS_RUNNING = "running";
        public static final String STATUS_COMPLETED = "completed";
        public static final String STATUS_PARTIAL_FAILED = "partial_failed";
        public static final String STATUS_FAILED = "failed";
        public static final String STATUS_CANCELLED = "cancelled";
        public static final String ITEM_PENDING = "pending";
        public static final String ITEM_RUNNING = "running";
        public static final String ITEM_COMPLETED = "completed";
        public static final String ITEM_FAILED = "failed";
        public static final String ENTRY_READY = "ready";
        public static final String ENTRY_LOW_CONFIDENCE = "low_confidence";
        public static final String ENTRY_FAILED = "failed";
        public static final String SOURCE_AI = "ai";
        public static final String STRATEGY_VERSION = "semantic_coordinator_v1";
        public static final int DEFAULT_BATCH_SIZE = 25;
        public static final int MIN_BATCH_SIZE = 10;
        public static final int MAX_BATCH_SIZE = 50;
        public static final int MAX_TAG_COUNT = 6;
        public static final int MAX_RELATED_COUNT = 12;
        public static final double LOW_CONFIDENCE_THRESHOLD = 0.55D;

        private VocabularyCatalogAnalysisConstants() {
        }
}
