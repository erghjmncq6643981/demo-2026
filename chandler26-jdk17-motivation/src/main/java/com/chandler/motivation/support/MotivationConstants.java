package com.chandler.motivation.support;

public final class MotivationConstants {

    private MotivationConstants() {
    }

    public static final class UserType {
        public static final String PARENT = "PARENT";
        public static final String GUARDIAN = "GUARDIAN";
        public static final String CHILD = "CHILD";

        private UserType() {
        }
    }

    public static final class ChildStatus {
        public static final String ACTIVE = "ACTIVE";
        public static final String INACTIVE = "INACTIVE";

        private ChildStatus() {
        }
    }

    public static final class FamilyRole {
        public static final String PARENT = "PARENT";
        public static final String GUARDIAN = "GUARDIAN";

        private FamilyRole() {
        }
    }

    public static final class GoalStatus {
        public static final String ACTIVE = "ACTIVE";
        public static final String PAUSED = "PAUSED";
        public static final String FINISHED = "FINISHED";

        private GoalStatus() {
        }
    }

    public static final class TaskStatus {
        public static final String ACTIVE = "ACTIVE";
        public static final String PAUSED = "PAUSED";
        public static final String ARCHIVED = "ARCHIVED";
        public static final String PENDING = "PENDING";
        public static final String SUBMITTED = "SUBMITTED";
        public static final String APPROVED = "APPROVED";
        public static final String REJECTED = "REJECTED";
        public static final String SKIPPED = "SKIPPED";

        private TaskStatus() {
        }
    }

    public static final class PeriodType {
        public static final String DAILY = "DAILY";
        public static final String WEEKLY = "WEEKLY";
        public static final String MONTHLY = "MONTHLY";

        private PeriodType() {
        }
    }

    public static final class PointType {
        public static final String STAR = "STAR";
        public static final String FLOWER = "FLOWER";
        public static final String CROWN = "CROWN";

        private PointType() {
        }
    }

    public static final class RewardStatus {
        public static final String ACTIVE = "ACTIVE";
        public static final String PAUSED = "PAUSED";
        public static final String ARCHIVED = "ARCHIVED";

        private RewardStatus() {
        }
    }

    public static final class RewardExchangeStatus {
        public static final String REQUESTED = "REQUESTED";
        public static final String APPROVED = "APPROVED";
        public static final String REJECTED = "REJECTED";
        public static final String COMPLETED = "COMPLETED";
        public static final String CANCELLED = "CANCELLED";

        private RewardExchangeStatus() {
        }
    }

    public static final class LedgerSourceType {
        public static final String TASK_RECORD = "TASK_RECORD";
        public static final String MANUAL_ADJUST = "MANUAL_ADJUST";
        public static final String REWARD_EXCHANGE = "REWARD_EXCHANGE";
        public static final String SYSTEM = "SYSTEM";

        private LedgerSourceType() {
        }
    }

    public static final class LogType {
        public static final String AUTH = "AUTH";
        public static final String TASK = "TASK";
        public static final String POINT = "POINT";
        public static final String REWARD = "REWARD";
        public static final String CALENDAR = "CALENDAR";
        public static final String SYSTEM = "SYSTEM";

        private LogType() {
        }
    }

    public static final class LogSource {
        public static final String SERVER = "SERVER";
        public static final String CLIENT = "CLIENT";

        private LogSource() {
        }
    }

    public static final class ExchangeLimitType {
        public static final String UNLIMITED = "UNLIMITED";
        public static final String DAILY = "DAILY";
        public static final String WEEKLY = "WEEKLY";
        public static final String MONTHLY = "MONTHLY";

        private ExchangeLimitType() {
        }
    }

    public static final class ErrorCode {
        public static final String AUTH_REQUIRED = "AUTH_REQUIRED";
        public static final String AUTH_INVALID = "AUTH_INVALID";
        public static final String AUTH_EXPIRED = "AUTH_EXPIRED";
        public static final String USER_ALREADY_EXISTS = "USER_ALREADY_EXISTS";

        private ErrorCode() {
        }
    }
}
