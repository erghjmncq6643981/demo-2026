package com.chandler.learning.agent.task.application.contract;

import java.time.LocalDate;
import java.util.Map;

/** 类型安全读取任务持久化参数，业务 ID 始终按字符串传递后再转换。 */
public final class AiTaskPayload {

    private AiTaskPayload() {
    }

    /** 从任务载荷读取长整型 ID 参数。 */
    public static Long longValue(Map<String, Object> payload, String key) {
        Object value = payload == null ? null : payload.get(key);
        if (value == null) return null;
        try {
            return value instanceof Number number ? number.longValue() : Long.valueOf(value.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /** 从任务载荷读取整数参数。 */
    public static Integer intValue(Map<String, Object> payload, String key, Integer defaultValue) {
        Object value = payload == null ? null : payload.get(key);
        if (value == null) return defaultValue;
        try {
            return value instanceof Number number ? number.intValue() : Integer.valueOf(value.toString());
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    /** 从任务载荷读取日期参数。 */
    public static LocalDate dateValue(Map<String, Object> payload, String key, LocalDate defaultValue) {
        Object value = payload == null ? null : payload.get(key);
        if (value == null) return defaultValue;
        try {
            return LocalDate.parse(value.toString());
        } catch (RuntimeException ex) {
            return defaultValue;
        }
    }
}
