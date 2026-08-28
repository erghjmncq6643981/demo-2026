package com.chandler.learning.agent.task.domain.constant;

/** AiTask 业务常量。 */
public final class AiTaskConstants {

public static final String TYPE_SCENE_MATERIAL = "scene_material";
        public static final String TYPE_SCENE_MATERIAL_REGENERATION = "scene_material_regeneration";
        public static final String TYPE_SCENE_RELATED_VOCABULARY = "scene_related_vocabulary";
        public static final String TYPE_VOCABULARY_CARD = "vocabulary_card";
        public static final String TYPE_VOCABULARY_CATALOG_ANALYSIS = "vocabulary_catalog_analysis";
        public static final String TYPE_ARTICLE_MATERIAL = "article_material";
        public static final String STATUS_PENDING = "pending";
        public static final String STATUS_RUNNING = "running";
        public static final String STATUS_RETRY_WAIT = "retry_wait";
        public static final String STATUS_COMPLETED = "completed";
        public static final String STATUS_PARTIAL_FAILED = "partial_failed";
        public static final String STATUS_ATTENTION_REQUIRED = "attention_required";
        public static final String STATUS_FAILED = "failed";
        public static final String STATUS_CANCELLED = "cancelled";
        public static final String EXECUTION_IMMEDIATE = "immediate";
        public static final String EXECUTION_SCHEDULED = "scheduled";
        public static final String EXECUTION_LOW_COST_WINDOW = "low_cost_window";
        public static final int DEFAULT_PRIORITY = 50;
        public static final int DEFAULT_MAX_RETRY_COUNT = 2;
        public static final int DEFAULT_PAGE_SIZE = 50;
        public static final int MAX_PAGE_SIZE = 100;
        public static final int RUNNING_TIMEOUT_MINUTES = 5;
        public static final int QUEUE_RETRY_DELAY_SECONDS = 10;
        public static final int STEP_LEASE_MINUTES = 5;
        /** AI 步骤租约续期间隔，必须明显短于租约时长。 */
        public static final int STEP_HEARTBEAT_INTERVAL_SECONDS = 60;
        public static final int RETRY_BASE_DELAY_SECONDS = 30;
        public static final int MAX_RETRY_DELAY_SECONDS = 1800;
        public static final String RUNNING_TIMEOUT_MESSAGE = "AI 任务执行超时，已转为失败，可手动重试";

        private AiTaskConstants() {
        }
}
