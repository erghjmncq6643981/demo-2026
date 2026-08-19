package com.chandler.learning.agent.ai.gateway.parser;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

/**
 * DeepSeek 结构化输出解析器。
 * <p>
 * DeepSeek 正常返回可直接读取的 JSON；仅当直读失败且内容带有思考包裹时，移除包裹后重试。
 */
public class DeepSeekJsonResponseParser extends StrictJsonResponseParser {

    public DeepSeekJsonResponseParser(ObjectMapper objectMapper) {
        super(objectMapper);
    }

    @Override
    public String name() {
        return "deepseek-json";
    }

    @Override
    public boolean supports(String provider, String modelName) {
        return normalized(provider).equals("deepseek") || normalized(modelName).startsWith("deepseek-");
    }

    @Override
    public AiStructuredResponseParseResult parse(String content) {
        try {
            return super.parse(content);
        } catch (AiStructuredResponseParseException strictFailure) {
            String answer = removeThinkingEnvelope(content);
            if (!StringUtils.hasText(answer) || answer.equals(content)) {
                throw strictFailure;
            }
            try {
                return success(read(answer.trim()), "thinking_envelope", List.of("removed_thinking_envelope"));
            } catch (JsonProcessingException ex) {
                String candidate = JsonResponseParserSupport.extractFirstBalancedJson(answer);
                if (candidate != null) {
                    try {
                        return success(read(candidate), "thinking_envelope_balanced",
                                List.of("removed_thinking_envelope", "extracted_json_block"));
                    } catch (JsonProcessingException ignored) {
                        // Report the original provider-specific recovery failure below.
                    }
                }
                throw failure("thinking_envelope", List.of("removed_thinking_envelope"), ex);
            }
        }
    }

    private String removeThinkingEnvelope(String content) {
        if (!StringUtils.hasText(content)) {
            return content;
        }
        int end = content.toLowerCase(Locale.ROOT).indexOf("</think>");
        return end < 0 ? content : content.substring(end + "</think>".length()).trim();
    }

    private String normalized(String value) {
        return StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : "";
    }
}
