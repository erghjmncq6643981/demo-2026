package com.chandler.motivation.support;

/**
 * 业务常量兼容层。
 * <p>
 * 新增代码优先使用 {@link MotivationEnums}，这里保留英文编码，避免影响已经入库的数据和前端协议。
 */
public final class MotivationConstants {

    private MotivationConstants() {
    }

    public static final class Flag {
        public static final int YES = 1;
        public static final int NO = 0;

        private Flag() {
        }
    }

    public static final class Pagination {
        public static final int DEFAULT_LIMIT = 20;
        public static final int MIN_LIMIT = 1;
        public static final int LEDGER_MAX_LIMIT = 200;
        public static final int REWARD_EXCHANGE_MAX_LIMIT = 100;
        public static final int ACTIVITY_LOG_MAX_LIMIT = 50;

        private Pagination() {
        }
    }

    public static final class Schedule {
        public static final int DEFAULT_START_HOUR = 6;
        public static final int DEFAULT_END_HOUR = 22;
        public static final int MIN_REQUIRED_COUNT = 1;
        public static final int FULL_PROGRESS = 100;
        public static final int EMPTY_PROGRESS = 0;
        public static final int FIRST_WEEK_DAY = 1;
        public static final int LAST_WEEK_DAY = 7;
        public static final int FIRST_MONTH_DAY = 1;
        public static final int LAST_MONTH_DAY = 31;

        private Schedule() {
        }
    }

    public static final class Sort {
        public static final int DEFAULT_SORT_NO = 0;

        private Sort() {
        }
    }

    public static final class Avatar {
        public static final long MAX_BYTES = 1_048_576L;
        public static final int MAX_DIMENSION = 256;
        public static final float JPEG_QUALITY = 0.82F;
        public static final String CONTENT_TYPE_JPEG = "image/jpeg";

        private Avatar() {
        }
    }

    public static final class UserType {
        public static final String PARENT = MotivationEnums.UserType.PARENT.code();
        public static final String GUARDIAN = MotivationEnums.UserType.GUARDIAN.code();
        public static final String CHILD = MotivationEnums.UserType.CHILD.code();

        private UserType() {
        }
    }

    public static final class ChildStatus {
        public static final String ACTIVE = MotivationEnums.ChildStatus.ACTIVE.code();
        public static final String INACTIVE = MotivationEnums.ChildStatus.INACTIVE.code();

        private ChildStatus() {
        }
    }

    public static final class FamilyRole {
        public static final String PARENT = MotivationEnums.FamilyRole.PARENT.code();
        public static final String GUARDIAN = MotivationEnums.FamilyRole.GUARDIAN.code();
        public static final String CHILD = MotivationEnums.FamilyRole.CHILD.code();

        private FamilyRole() {
        }
    }

    public static final class GoalStatus {
        public static final String ACTIVE = MotivationEnums.GoalStatus.ACTIVE.code();
        public static final String PAUSED = MotivationEnums.GoalStatus.PAUSED.code();
        public static final String FINISHED = MotivationEnums.GoalStatus.FINISHED.code();

        private GoalStatus() {
        }
    }

    public static final class TaskStatus {
        public static final String ACTIVE = MotivationEnums.TaskStatus.ACTIVE.code();
        public static final String PAUSED = MotivationEnums.TaskStatus.PAUSED.code();
        public static final String ARCHIVED = MotivationEnums.TaskStatus.ARCHIVED.code();
        public static final String PENDING = MotivationEnums.TaskStatus.PENDING.code();
        public static final String SUBMITTED = MotivationEnums.TaskStatus.SUBMITTED.code();
        public static final String APPROVED = MotivationEnums.TaskStatus.APPROVED.code();
        public static final String REJECTED = MotivationEnums.TaskStatus.REJECTED.code();
        public static final String SKIPPED = MotivationEnums.TaskStatus.SKIPPED.code();

        private TaskStatus() {
        }
    }

    public static final class PeriodType {
        public static final String DAILY = MotivationEnums.PeriodType.DAILY.code();
        public static final String WEEKLY = MotivationEnums.PeriodType.WEEKLY.code();
        public static final String MONTHLY = MotivationEnums.PeriodType.MONTHLY.code();

        private PeriodType() {
        }
    }

    public static final class PointType {
        public static final String STAR = MotivationEnums.PointType.STAR.code();
        public static final String FLOWER = MotivationEnums.PointType.FLOWER.code();
        public static final String CROWN = MotivationEnums.PointType.CROWN.code();

        private PointType() {
        }
    }

    public static final class RewardStatus {
        public static final String ACTIVE = MotivationEnums.RewardStatus.ACTIVE.code();
        public static final String PAUSED = MotivationEnums.RewardStatus.PAUSED.code();
        public static final String ARCHIVED = MotivationEnums.RewardStatus.ARCHIVED.code();

        private RewardStatus() {
        }
    }

    public static final class RewardFulfillmentType {
        public static final String INVENTORY_DEDUCT = MotivationEnums.RewardFulfillmentType.INVENTORY_DEDUCT.code();
        public static final String PARENT_EXECUTE = MotivationEnums.RewardFulfillmentType.PARENT_EXECUTE.code();
        public static final String PARENT_PURCHASE = MotivationEnums.RewardFulfillmentType.PARENT_PURCHASE.code();
        public static final String PARENT_FULFILL = MotivationEnums.RewardFulfillmentType.PARENT_FULFILL.code();

        private RewardFulfillmentType() {
        }
    }

    public static final class RewardFulfillmentStatus {
        public static final String PENDING = MotivationEnums.RewardFulfillmentStatus.PENDING.code();
        public static final String SCHEDULED = MotivationEnums.RewardFulfillmentStatus.SCHEDULED.code();
        public static final String IN_PROGRESS = MotivationEnums.RewardFulfillmentStatus.IN_PROGRESS.code();
        public static final String COMPLETED = MotivationEnums.RewardFulfillmentStatus.COMPLETED.code();
        public static final String CONFIRMED = MotivationEnums.RewardFulfillmentStatus.CONFIRMED.code();

        private RewardFulfillmentStatus() {
        }
    }

    public static final class RewardExchangeStatus {
        public static final String REQUESTED = MotivationEnums.RewardExchangeStatus.REQUESTED.code();
        public static final String APPROVED = MotivationEnums.RewardExchangeStatus.APPROVED.code();
        public static final String REJECTED = MotivationEnums.RewardExchangeStatus.REJECTED.code();
        public static final String COMPLETED = MotivationEnums.RewardExchangeStatus.COMPLETED.code();
        public static final String CANCELLED = MotivationEnums.RewardExchangeStatus.CANCELLED.code();

        private RewardExchangeStatus() {
        }
    }

    public static final class LedgerSourceType {
        public static final String TASK_RECORD = MotivationEnums.LedgerSourceType.TASK_RECORD.code();
        public static final String MANUAL_ADJUST = MotivationEnums.LedgerSourceType.MANUAL_ADJUST.code();
        public static final String REWARD_EXCHANGE = MotivationEnums.LedgerSourceType.REWARD_EXCHANGE.code();
        public static final String REWARD_EXCHANGE_CHANGE = MotivationEnums.LedgerSourceType.REWARD_EXCHANGE_CHANGE.code();
        public static final String REWARD_EXCHANGE_REFUND = MotivationEnums.LedgerSourceType.REWARD_EXCHANGE_REFUND.code();
        public static final String POINT_EXCHANGE = MotivationEnums.LedgerSourceType.POINT_EXCHANGE.code();
        public static final String SYSTEM = MotivationEnums.LedgerSourceType.SYSTEM.code();

        private LedgerSourceType() {
        }
    }

    public static final class LogType {
        public static final String AUTH = MotivationEnums.LogType.AUTH.code();
        public static final String TASK = MotivationEnums.LogType.TASK.code();
        public static final String POINT = MotivationEnums.LogType.POINT.code();
        public static final String REWARD = MotivationEnums.LogType.REWARD.code();
        public static final String CALENDAR = MotivationEnums.LogType.CALENDAR.code();
        public static final String SYSTEM = MotivationEnums.LogType.SYSTEM.code();

        private LogType() {
        }
    }

    public static final class LogSource {
        public static final String BUSINESS = MotivationEnums.LogSource.BUSINESS.code();
        public static final String SYSTEM = MotivationEnums.LogSource.SYSTEM.code();
        public static final String SERVER = MotivationEnums.LogSource.SERVER.code();
        public static final String CLIENT = MotivationEnums.LogSource.CLIENT.code();

        private LogSource() {
        }
    }

    public static final class ExchangeLimitType {
        public static final String UNLIMITED = MotivationEnums.ExchangeLimitType.UNLIMITED.code();
        public static final String DAILY = MotivationEnums.ExchangeLimitType.DAILY.code();
        public static final String WEEKLY = MotivationEnums.ExchangeLimitType.WEEKLY.code();
        public static final String MONTHLY = MotivationEnums.ExchangeLimitType.MONTHLY.code();

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
