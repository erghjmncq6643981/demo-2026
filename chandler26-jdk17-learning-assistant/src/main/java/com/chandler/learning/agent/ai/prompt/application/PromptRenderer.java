package com.chandler.learning.agent.ai.prompt.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.PropertyPlaceholderHelper;

import java.util.Map;

/**
 * {{variable}} 风格提示词渲染器。
 */
@Component
@RequiredArgsConstructor
public class PromptRenderer {

    private static final PropertyPlaceholderHelper PLACEHOLDER_HELPER =
            new PropertyPlaceholderHelper("{{", "}}", null, true);

    private final ObjectMapper objectMapper;

    /** 渲染提示词模板。 */
    public String render(String template, Map<String, Object> variables) {
        if (template == null || variables == null || variables.isEmpty()) {
            return template;
        }
        return PLACEHOLDER_HELPER.replacePlaceholders(template, placeholderName -> {
            Object value = variables.get(placeholderName);
            if (value == null) {
                return "";
            }
            return value instanceof String ? (String) value : toJson(value);
        });
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return String.valueOf(value);
        }
    }
}
