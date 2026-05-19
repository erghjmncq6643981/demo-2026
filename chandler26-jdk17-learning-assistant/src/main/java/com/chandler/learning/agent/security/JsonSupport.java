package com.chandler.learning.agent.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

final class JsonSupport {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private JsonSupport() {
    }

    static String toJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("JSON 序列化失败", ex);
        }
    }

    static Map<String, Object> fromJson(String value) {
        try {
            return OBJECT_MAPPER.readValue(value, MAP_TYPE);
        } catch (Exception ex) {
            throw new IllegalArgumentException("JSON 解析失败", ex);
        }
    }
}
