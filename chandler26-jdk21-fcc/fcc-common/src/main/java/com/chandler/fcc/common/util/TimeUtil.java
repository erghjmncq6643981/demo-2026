package com.chandler.fcc.common.util;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * 电信级高精度时间处理工具类
 * <p>
 * 遵循 FCC 系统数据存储原则：
 * <ul>
 *   <li>所有数据库存储及网络传输时间统一以 UTC 毫秒时间戳或 UTC datetime(3) 写入</li>
 *   <li>持续时间统一以毫秒 (ms) 计算与传递</li>
 * </ul>
 * </p>
 *
 * @author Chandler
 */
public final class TimeUtil {

    /**
     * UTC 时区常量
     */
    public static final ZoneId UTC_ZONE = ZoneOffset.UTC;

    /**
     * 系统默认展示时区（东八区）
     */
    public static final ZoneId CST_ZONE = ZoneId.of("Asia/Shanghai");

    /**
     * 毫秒精度标准日期时间格式化器 (yyyy-MM-dd HH:mm:ss.SSS)
     */
    public static final DateTimeFormatter DATETIME_MS_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    /**
     * 秒级日期时间格式化器 (yyyy-MM-dd HH:mm:ss)
     */
    public static final DateTimeFormatter DATETIME_SEC_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private TimeUtil() {
    }

    /**
     * 获取当前系统 UTC 时间戳（毫秒）
     *
     * @return 当前 UTC 毫秒数
     */
    public static long nowEpochMilli() {
        return System.currentTimeMillis();
    }

    /**
     * 获取当前 UTC 时间的 LocalDateTime
     *
     * @return 当前 UTC LocalDateTime 对象
     */
    public static LocalDateTime nowUtc() {
        return LocalDateTime.now(UTC_ZONE);
    }

    /**
     * 将毫秒时间戳转换为 UTC LocalDateTime
     *
     * @param epochMilli 毫秒时间戳
     * @return 对应的 UTC LocalDateTime，入参为 null 时返回 null
     */
    public static LocalDateTime toUtcLocalDateTime(Long epochMilli) {
        if (epochMilli == null) {
            return null;
        }
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMilli), UTC_ZONE);
    }

    /**
     * 将 LocalDateTime 转换为 UTC 毫秒时间戳
     *
     * @param dateTime LocalDateTime 对象
     * @return 对应的毫秒时间戳，入参为 null 时返回 null
     */
    public static Long toEpochMilli(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.toInstant(ZoneOffset.UTC).toEpochMilli();
    }

    /**
     * 格式化 LocalDateTime 为毫秒精度字符串 (yyyy-MM-dd HH:mm:ss.SSS)
     *
     * @param dateTime LocalDateTime 对象
     * @return 格式化后的字符串，入参为 null 返回空串
     */
    public static String formatWithMs(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "";
        }
        return dateTime.format(DATETIME_MS_FORMATTER);
    }

    /**
     * 解析毫秒精度日期时间字符串为 LocalDateTime
     *
     * @param text 格式为 yyyy-MM-dd HH:mm:ss.SSS 的字符串
     * @return 解析所得 LocalDateTime 对象
     */
    public static LocalDateTime parseWithMs(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        return LocalDateTime.parse(text.trim(), DATETIME_MS_FORMATTER);
    }

    /**
     * 计算两个时间点之间的持续毫秒数
     *
     * @param start 起始时间
     * @param end   结束时间
     * @return 持续毫秒数，若任一入参为 null 则返回 null
     */
    public static Long calcDurationMs(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) {
            return null;
        }
        long startMs = start.toInstant(ZoneOffset.UTC).toEpochMilli();
        long endMs = end.toInstant(ZoneOffset.UTC).toEpochMilli();
        return Math.max(0L, endMs - startMs);
    }
}
