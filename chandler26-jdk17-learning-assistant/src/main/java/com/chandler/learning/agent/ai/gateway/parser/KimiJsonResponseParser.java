package com.chandler.learning.agent.ai.gateway.parser;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Kimi / Moonshot 结构化输出解析器。
 * <p>
 * 先执行严格解析。只有失败后，才修复 JSON 结构外的全角分隔符、尾逗号、少量已知字段的裸字符串
 * 和响应结尾遗漏的闭合括号；不会在已合法字符串中替换中文引号或标点。
 */
public class KimiJsonResponseParser extends StrictJsonResponseParser {

    public KimiJsonResponseParser(ObjectMapper objectMapper) {
        super(objectMapper);
    }

    /** 返回 Kimi JSON 解析器名称。 */
    @Override
    public String name() {
        return "kimi-json";
    }

    /** 判断供应商或模型名称是否属于 Kimi/Moonshot。 */
    @Override
    public boolean supports(String provider, String modelName) {
        String providerKey = normalized(provider);
        String modelKey = normalized(modelName);
        return "kimi".equals(providerKey) || "moonshot".equals(providerKey)
                || modelKey.contains("moonshot") || modelKey.contains("kimi");
    }

    /** 优先严格解析，失败后执行受限的 Kimi JSON 兼容修复。 */
    @Override
    public AiStructuredResponseParseResult parse(String content) {
        try {
            return super.parse(content);
        } catch (AiStructuredResponseParseException strictFailure) {
            return repairAndParse(content, strictFailure);
        }
    }

    private AiStructuredResponseParseResult repairAndParse(String content, AiStructuredResponseParseException strictFailure) {
        if (!StringUtils.hasText(content)) {
            throw strictFailure;
        }
        List<String> repairs = new ArrayList<>();
        String candidate = JsonResponseParserSupport.unwrapCodeFence(content);
        if (candidate == null) {
            candidate = JsonResponseParserSupport.sliceFromFirstJsonStart(content);
        } else {
            repairs.add("removed_code_fence");
        }
        if (!StringUtils.hasText(candidate)) {
            throw strictFailure;
        }

        String normalized = JsonResponseParserSupport.normalizeStructuralPunctuation(candidate);
        if (!normalized.equals(candidate)) {
            repairs.add("normalized_structural_punctuation");
        }
        String withCommas = JsonResponseParserSupport.insertMissingCommasBetweenObjects(normalized);
        if (!withCommas.equals(normalized)) {
            repairs.add("inserted_missing_commas");
        }
        String withWrappedArrays = JsonResponseParserSupport.wrapUnbracketedArrayFields(withCommas);
        if (!withWrappedArrays.equals(withCommas)) {
            repairs.add("wrapped_unbracketed_array");
        }
        String withoutTrailingCommas = JsonResponseParserSupport.removeTrailingCommas(withWrappedArrays);
        if (!withoutTrailingCommas.equals(withWrappedArrays)) {
            repairs.add("removed_trailing_comma");
        }
        normalized = withoutTrailingCommas;
        // Quote known bare string values first.
        String quotedValues = JsonResponseParserSupport.quoteKnownBareStringValues(normalized);
        if (!quotedValues.equals(normalized)) {
            repairs.add("quoted_known_bare_value");
        }
        // Then quote known bare key names.
        String quoted = JsonResponseParserSupport.quoteKnownBareKeyNames(quotedValues);
        if (!quoted.equals(quotedValues)) {
            repairs.add("quoted_known_bare_key");
        }
        String completed = JsonResponseParserSupport.completeClosingBrackets(quoted);
        if (!completed.equals(quoted)) {
            repairs.add("completed_trailing_brackets");
        }

        try {
            return success(read(completed), "repaired", repairs);
        } catch (JsonProcessingException ex) {
            String block = JsonResponseParserSupport.extractFirstBalancedJson(completed);
            if (block != null) {
                try {
                    repairs.add("extracted_json_block");
                    return success(read(block), "repaired_balanced", repairs);
                } catch (JsonProcessingException ignored) {
                    // Preserve the repair attempt and cause for AI audit diagnostics.
                }
            }
            throw failure("repaired", repairs, ex);
        }
    }

    private String normalized(String value) {
        return StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : "";
    }
}
