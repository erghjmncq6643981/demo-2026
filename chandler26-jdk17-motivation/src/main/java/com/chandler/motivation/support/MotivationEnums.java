package com.chandler.motivation.support;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import org.springframework.util.StringUtils;

/**
 * 激励系统的业务枚举。
 * <p>
 * 数据库存储稳定的英文编码，业务日志和异常提示使用中文描述，避免在服务中散落魔法字符串。
 */
public final class MotivationEnums {

    private MotivationEnums() {
    }

    /**
     * 所有业务枚举统一暴露编码和中文描述。
     */
    public interface DescribedEnum {
        String code();

        String description();
    }

    public static <E extends Enum<E> & DescribedEnum> E fromCode(Class<E> enumClass, String code, E fallback) {
        if (!StringUtils.hasText(code)) {
            return fallback;
        }
        return Arrays.stream(enumClass.getEnumConstants())
                .filter((item) -> item.code().equalsIgnoreCase(code.trim()))
                .findFirst()
                .orElse(fallback);
    }

    public static <E extends Enum<E> & DescribedEnum> boolean codeEquals(E value, String code) {
        return value != null && StringUtils.hasText(code) && value.code().equalsIgnoreCase(code.trim());
    }

    public static <E extends Enum<E> & DescribedEnum> String descriptionOf(Class<E> enumClass, String code, E fallback) {
        E resolved = fromCode(enumClass, code, fallback);
        return resolved == null ? "" : resolved.description();
    }

    public enum UserType implements DescribedEnum {
        PARENT("PARENT", "家长"),
        GUARDIAN("GUARDIAN", "监护人"),
        CHILD("CHILD", "孩子");

        @EnumValue
        private final String code;
        private final String description;

        UserType(String code, String description) {
            this.code = code;
            this.description = description;
        }

        @Override
        @JsonValue
        public String code() {
            return code;
        }

        @Override
        public String description() {
            return description;
        }
    }

    public enum ChildStatus implements DescribedEnum {
        ACTIVE("ACTIVE", "启用"),
        INACTIVE("INACTIVE", "停用");

        @EnumValue
        private final String code;
        private final String description;

        ChildStatus(String code, String description) {
            this.code = code;
            this.description = description;
        }

        @Override
        @JsonValue
        public String code() {
            return code;
        }

        @Override
        public String description() {
            return description;
        }
    }

    public enum FamilyRole implements DescribedEnum {
        PARENT("PARENT", "家长"),
        GUARDIAN("GUARDIAN", "监护人"),
        CHILD("CHILD", "孩子");

        @EnumValue
        private final String code;
        private final String description;

        FamilyRole(String code, String description) {
            this.code = code;
            this.description = description;
        }

        @Override
        @JsonValue
        public String code() {
            return code;
        }

        @Override
        public String description() {
            return description;
        }
    }

    public enum GoalStatus implements DescribedEnum {
        ACTIVE("ACTIVE", "进行中"),
        PAUSED("PAUSED", "已暂停"),
        FINISHED("FINISHED", "已完成");

        @EnumValue
        private final String code;
        private final String description;

        GoalStatus(String code, String description) {
            this.code = code;
            this.description = description;
        }

        @Override
        @JsonValue
        public String code() {
            return code;
        }

        @Override
        public String description() {
            return description;
        }
    }

    public enum TaskStatus implements DescribedEnum {
        ACTIVE("ACTIVE", "启用"),
        PAUSED("PAUSED", "已暂停"),
        ARCHIVED("ARCHIVED", "已归档"),
        PENDING("PENDING", "待打卡"),
        SUBMITTED("SUBMITTED", "待审核"),
        APPROVED("APPROVED", "已完成"),
        REJECTED("REJECTED", "已拒绝"),
        SKIPPED("SKIPPED", "已跳过");

        @EnumValue
        private final String code;
        private final String description;

        TaskStatus(String code, String description) {
            this.code = code;
            this.description = description;
        }

        @Override
        @JsonValue
        public String code() {
            return code;
        }

        @Override
        public String description() {
            return description;
        }
    }

    public enum PeriodType implements DescribedEnum {
        DAILY("DAILY", "每日"),
        WEEKLY("WEEKLY", "每周"),
        MONTHLY("MONTHLY", "每月");

        @EnumValue
        private final String code;
        private final String description;

        PeriodType(String code, String description) {
            this.code = code;
            this.description = description;
        }

        @Override
        @JsonValue
        public String code() {
            return code;
        }

        @Override
        public String description() {
            return description;
        }
    }

    public enum TaskCategory implements DescribedEnum {
        STUDY("STUDY", "学习"),
        LIFE("LIFE", "生活"),
        SPORT("SPORT", "运动"),
        HABIT("HABIT", "习惯");

        @EnumValue
        private final String code;
        private final String description;

        TaskCategory(String code, String description) {
            this.code = code;
            this.description = description;
        }

        @Override
        @JsonValue
        public String code() {
            return code;
        }

        @Override
        public String description() {
            return description;
        }
    }

    public enum PointType implements DescribedEnum {
        STAR("STAR", "星星"),
        FLOWER("FLOWER", "红花"),
        CROWN("CROWN", "皇冠");

        @EnumValue
        private final String code;
        private final String description;

        PointType(String code, String description) {
            this.code = code;
            this.description = description;
        }

        @Override
        @JsonValue
        public String code() {
            return code;
        }

        @Override
        public String description() {
            return description;
        }
    }

    public enum RewardStatus implements DescribedEnum {
        ACTIVE("ACTIVE", "上架"),
        PAUSED("PAUSED", "暂停"),
        ARCHIVED("ARCHIVED", "已归档");

        @EnumValue
        private final String code;
        private final String description;

        RewardStatus(String code, String description) {
            this.code = code;
            this.description = description;
        }

        @Override
        @JsonValue
        public String code() {
            return code;
        }

        @Override
        public String description() {
            return description;
        }
    }

    public enum RewardFulfillmentType implements DescribedEnum {
        INVENTORY_DEDUCT("INVENTORY_DEDUCT", "库存扣减"),
        PARENT_EXECUTE("PARENT_EXECUTE", "家长执行"),
        PARENT_PURCHASE("PARENT_PURCHASE", "家长购买"),
        PARENT_FULFILL("PARENT_FULFILL", "家长实现");

        @EnumValue
        private final String code;
        private final String description;

        RewardFulfillmentType(String code, String description) {
            this.code = code;
            this.description = description;
        }

        @Override
        @JsonValue
        public String code() {
            return code;
        }

        @Override
        public String description() {
            return description;
        }
    }

    public enum RewardFulfillmentStatus implements DescribedEnum {
        PENDING("PENDING", "待处理"),
        SCHEDULED("SCHEDULED", "已加入日程"),
        IN_PROGRESS("IN_PROGRESS", "待实现"),
        COMPLETED("COMPLETED", "已实现"),
        CONFIRMED("CONFIRMED", "孩子已确认");

        @EnumValue
        private final String code;
        private final String description;

        RewardFulfillmentStatus(String code, String description) {
            this.code = code;
            this.description = description;
        }

        @Override
        @JsonValue
        public String code() {
            return code;
        }

        @Override
        public String description() {
            return description;
        }
    }

    public enum RewardBranchStatus implements DescribedEnum {
        PENDING("PENDING", "待处理"),
        PURCHASE_ORDERED("PURCHASE_ORDERED", "家长已下单"),
        PURCHASE_SHIPPING("PURCHASE_SHIPPING", "奖励运输中"),
        PURCHASE_ARRIVED("PURCHASE_ARRIVED", "奖励已到货"),
        SCHEDULED("SCHEDULED", "已加入日程"),
        IN_PROGRESS("IN_PROGRESS", "奖励进行中"),
        COMPLETED("COMPLETED", "已完成");

        @EnumValue
        private final String code;
        private final String description;

        RewardBranchStatus(String code, String description) {
            this.code = code;
            this.description = description;
        }

        @Override
        @JsonValue
        public String code() {
            return code;
        }

        @Override
        public String description() {
            return description;
        }
    }

    public enum RewardExchangeStatus implements DescribedEnum {
        REQUESTED("REQUESTED", "待确认"),
        APPROVED("APPROVED", "已通过"),
        REJECTED("REJECTED", "已拒绝"),
        COMPLETED("COMPLETED", "已完成"),
        CANCELLED("CANCELLED", "已取消");

        @EnumValue
        private final String code;
        private final String description;

        RewardExchangeStatus(String code, String description) {
            this.code = code;
            this.description = description;
        }

        @Override
        @JsonValue
        public String code() {
            return code;
        }

        @Override
        public String description() {
            return description;
        }
    }

    public enum LedgerSourceType implements DescribedEnum {
        TASK_RECORD("TASK_RECORD", "任务打卡"),
        MANUAL_ADJUST("MANUAL_ADJUST", "手动调整"),
        REWARD_EXCHANGE("REWARD_EXCHANGE", "奖励兑换"),
        REWARD_EXCHANGE_CHANGE("REWARD_EXCHANGE_CHANGE", "奖励兑换找零"),
        REWARD_EXCHANGE_REFUND("REWARD_EXCHANGE_REFUND", "奖励兑换退回"),
        POINT_EXCHANGE("POINT_EXCHANGE", "币值互换"),
        SYSTEM("SYSTEM", "系统处理");

        @EnumValue
        private final String code;
        private final String description;

        LedgerSourceType(String code, String description) {
            this.code = code;
            this.description = description;
        }

        @Override
        @JsonValue
        public String code() {
            return code;
        }

        @Override
        public String description() {
            return description;
        }
    }

    public enum LogType implements DescribedEnum {
        AUTH("AUTH", "账号"),
        TASK("TASK", "任务"),
        POINT("POINT", "积分"),
        REWARD("REWARD", "奖励"),
        CALENDAR("CALENDAR", "日历"),
        SYSTEM("SYSTEM", "系统");

        @EnumValue
        private final String code;
        private final String description;

        LogType(String code, String description) {
            this.code = code;
            this.description = description;
        }

        @Override
        @JsonValue
        public String code() {
            return code;
        }

        @Override
        public String description() {
            return description;
        }
    }

    public enum LogSource implements DescribedEnum {
        BUSINESS("BUSINESS", "业务日志"),
        SYSTEM("SYSTEM", "系统日志"),
        SERVER("SERVER", "服务端"),
        CLIENT("CLIENT", "客户端");

        @EnumValue
        private final String code;
        private final String description;

        LogSource(String code, String description) {
            this.code = code;
            this.description = description;
        }

        @Override
        @JsonValue
        public String code() {
            return code;
        }

        @Override
        public String description() {
            return description;
        }
    }

    public enum ActivityLogCategory implements DescribedEnum {
        GROWTH("GROWTH", "宝贝成长日志"),
        OPERATION("OPERATION", "操作日志");

        @EnumValue
        private final String code;
        private final String description;

        ActivityLogCategory(String code, String description) {
            this.code = code;
            this.description = description;
        }

        @Override
        @JsonValue
        public String code() {
            return code;
        }

        @Override
        public String description() {
            return description;
        }
    }

    public enum ExchangeLimitType implements DescribedEnum {
        UNLIMITED("UNLIMITED", "不限"),
        DAILY("DAILY", "每日"),
        WEEKLY("WEEKLY", "每周"),
        MONTHLY("MONTHLY", "每月");

        @EnumValue
        private final String code;
        private final String description;

        ExchangeLimitType(String code, String description) {
            this.code = code;
            this.description = description;
        }

        @Override
        @JsonValue
        public String code() {
            return code;
        }

        @Override
        public String description() {
            return description;
        }
    }
}
