package com.chandler.learning.agent.learning.application;

import com.chandler.learning.agent.common.exception.LearningErrorCode;
import com.chandler.learning.agent.exception.LearningAssistantException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 学习计划 AI JSON 的兼容读取与序列化辅助组件。
 * <p>集中处理模型字段别名和缺失字段错误，避免计划编排服务承担解析细节。</p>
 */
@Component
@RequiredArgsConstructor
public class LearningPlanJsonSupport {

    private final ObjectMapper objectMapper;

    /** 按候选字段名读取第一个非空节点。 */
    public JsonNode node(JsonNode source, String... keys) {
        if (source == null) {
            return null;
        }
        for (String key : keys) {
            JsonNode value = source.path(key);
            if (!value.isMissingNode() && !value.isNull()) {
                return value;
            }
        }
        return null;
    }

    /** 读取必填文本字段，缺失时返回可定位的业务错误。 */
    public String requiredText(JsonNode source, String... keys) {
        String value = text(source, keys);
        if (!StringUtils.hasText(value)) {
            throw LearningAssistantException.badRequest(
                    LearningErrorCode.LEARNING_SCENE_PARSE_FAILED,
                    "AI 场景结果缺少字段: " + String.join("/", keys));
        }
        return value;
    }

    /** 读取并清理文本字段。 */
    public String text(JsonNode source, String... keys) {
        JsonNode value = node(source, keys);
        return value != null && value.isValueNode() && StringUtils.hasText(value.asText())
                ? value.asText().trim() : null;
    }

    /** 序列化任务或场景 JSON，失败时转为稳定业务异常。 */
    public String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw LearningAssistantException.system(
                    LearningErrorCode.JSON_SERIALIZE_FAILED,
                    "AI 场景结果序列化失败", ex);
        }
    }
}
