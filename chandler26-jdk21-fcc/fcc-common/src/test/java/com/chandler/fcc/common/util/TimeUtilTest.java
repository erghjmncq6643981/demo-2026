package com.chandler.fcc.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TimeUtil 单元测试
 *
 * @author Chandler
 */
class TimeUtilTest {

    @Test
    @DisplayName("测试 UTC 时间转换与毫秒时间戳")
    void testUtcConversions() {
        long now = TimeUtil.nowEpochMilli();
        assertTrue(now > 1700000000000L);

        LocalDateTime utcDateTime = TimeUtil.toUtcLocalDateTime(now);
        assertNotNull(utcDateTime);

        Long epochBack = TimeUtil.toEpochMilli(utcDateTime);
        assertEquals(now, epochBack);
    }

    @Test
    @DisplayName("测试日期时间格式化与毫秒解析")
    void testFormatAndParse() {
        LocalDateTime now = TimeUtil.nowUtc();
        String formatted = TimeUtil.formatWithMs(now);
        assertNotNull(formatted);
        assertTrue(formatted.matches("^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3}$"));

        LocalDateTime parsed = TimeUtil.parseWithMs(formatted);
        assertNotNull(parsed);
        assertEquals(now.getYear(), parsed.getYear());
        assertEquals(now.getMonth(), parsed.getMonth());
        assertEquals(now.getDayOfMonth(), parsed.getDayOfMonth());
        assertEquals(now.getHour(), parsed.getHour());
        assertEquals(now.getMinute(), parsed.getMinute());
        assertEquals(now.getSecond(), parsed.getSecond());
    }

    @Test
    @DisplayName("测试持续时间计算")
    void testCalcDuration() {
        LocalDateTime start = TimeUtil.parseWithMs("2026-09-18 10:00:00.000");
        LocalDateTime end = TimeUtil.parseWithMs("2026-09-18 10:01:30.500");

        Long durationMs = TimeUtil.calcDurationMs(start, end);
        assertNotNull(durationMs);
        assertEquals(90500L, durationMs);
    }
}
