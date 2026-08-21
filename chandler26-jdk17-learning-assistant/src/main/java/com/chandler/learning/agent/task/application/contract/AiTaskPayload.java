package com.chandler.learning.agent.task.application.contract;

import java.time.LocalDate;
import java.util.Map;

/** 类型安全读取任务持久化参数，业务 ID 始终按字符串传递后再转换。 */
public final class AiTaskPayload {

    private AiTaskPayload() {
    }

    public static Long longValue(Map<String, Object> payload, String key) {
        Object value = payload == null ? null : payload.get(key);
        if (value == null) return null;
        try {
            return value instanceof Number number ? number.longValue() : Long.valueOf(value.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public static Integer intValue(Map<String, Object> payload, String key, Integer defaultValue) {
        Object value = payload == null ? null : payload.get(key);
        if (value == null) return defaultValue;
        try {
            return value instanceof Number number ? number.intValue() : Integer.valueOf(value.toString());
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

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
