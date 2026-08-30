package com.chandler.learning.agent.security;

import com.chandler.learning.agent.exception.LearningAssistantException;
import com.chandler.learning.agent.common.exception.LearningErrorCode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * 当前业务领域组件。
 */
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
            throw LearningAssistantException.system(
                    LearningErrorCode.JSON_SERIALIZE_FAILED,
                    "JSON 序列化失败",
                    ex);
        }
    }

    static Map<String, Object> fromJson(String value) {
        try {
            return OBJECT_MAPPER.readValue(value, MAP_TYPE);
        } catch (Exception ex) {
            throw LearningAssistantException.badRequest(
                    LearningErrorCode.JSON_PARSE_FAILED,
                    "JSON 解析失败");
        }
    }
}
