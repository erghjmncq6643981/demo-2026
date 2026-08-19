package com.chandler.learning.agent.ai.gateway.parser;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 通用严格 JSON 解析器。
 * <p>
 * 先直接读取原始文本，只有模型额外包裹 Markdown 或说明文字时才提取完整 JSON 块；
 * 不改写字符串内部标点，避免损坏正常中文内容。
 */
public class StrictJsonResponseParser implements AiStructuredResponseParser {

    protected final ObjectMapper objectMapper;

    public StrictJsonResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return "strict-json";
    }

    @Override
    public boolean supports(String provider, String modelName) {
        return true;
    }

    @Override
    public AiStructuredResponseParseResult parse(String content) {
        if (!StringUtils.hasText(content)) {
            throw failure("empty", List.of(), null);
        }
        try {
            return success(read(content.trim()), "raw", List.of());
        } catch (JsonProcessingException rawError) {
            String fenced = JsonResponseParserSupport.unwrapCodeFence(content);
            if (fenced != null) {
                try {
                    return success(read(fenced), "fence", List.of("removed_code_fence"));
                } catch (JsonProcessingException ignored) {
                    // Continue with a balanced JSON block rather than mutating valid string contents.
                }
            }
            String candidate = JsonResponseParserSupport.extractFirstBalancedJson(content);
            if (candidate != null) {
                try {
                    return success(read(candidate), "balanced", List.of("extracted_json_block"));
                } catch (JsonProcessingException ignored) {
                    // Preserve the original parsing failure as the root diagnostic below.
                }
            }
            throw failure("strict", List.of(), rawError);
        }
    }

    protected JsonNode read(String value) throws JsonProcessingException {
        return objectMapper.readTree(value);
    }

    protected AiStructuredResponseParseResult success(JsonNode root, String stage, List<String> repairs) {
        try {
            return new AiStructuredResponseParseResult(root, objectMapper.writeValueAsString(root), name(), stage, repairs);
        } catch (JsonProcessingException ex) {
            throw failure(stage, repairs, ex);
        }
    }

    protected AiStructuredResponseParseException failure(String stage, List<String> repairs, Throwable cause) {
        return new AiStructuredResponseParseException(name(), stage, repairs, cause);
    }
}
