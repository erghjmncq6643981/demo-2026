package com.chandler.learning.agent.learning.domain.constant;

/** 学习活动统计常量。 */
public final class LearningActivityConstants {

    /** 活动查询允许的最小天数。 */
    public static final int MIN_DAYS = 7;
    /** 活动查询默认天数。 */
    public static final int DEFAULT_DAYS = 365;
    /** 活动查询最多返回最近一年。 */
    public static final int MAX_DAYS = 365;
    /** 活动事件单批投影数量。 */
    public static final int PROJECT_BATCH_SIZE = 200;
    /** 活动事件处理状态。 */
    public static final String EVENT_PENDING = "pending";
    public static final String EVENT_PROCESSING = "processing";
    public static final String EVENT_SUCCEEDED = "succeeded";

    private LearningActivityConstants() {
    }
}
