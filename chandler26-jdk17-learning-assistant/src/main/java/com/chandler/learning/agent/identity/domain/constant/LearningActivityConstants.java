package com.chandler.learning.agent.identity.domain.constant;

/** Activity 业务常量。 */
public final class LearningActivityConstants {

        public static final int MIN_DAYS = 7;
        public static final int DEFAULT_DAYS = 90;
        /** 活跃图最多展示最近 90 天，避免把年度历史聚合放入首屏请求。 */
        public static final int MAX_DAYS = 90;

        private LearningActivityConstants() {
        }
}
