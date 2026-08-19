package com.chandler.learning.agent.support;

import org.springframework.util.StringUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** JSON 文本的无业务含义结构提取与受限修复工具。 */
final class JsonResponseParserSupport {

    private static final Pattern FENCED_JSON_PATTERN = Pattern.compile(
            "(?s)^\\s*```(?:json|JSON)?\\s*(.*?)\\s*```\\s*$");
    private static final Pattern KNOWN_BARE_VALUE_PATTERN = Pattern.compile(
            "\\\"(term|tier|mastery_requirement|phonetic|meaning|context_meaning|correct_answer|prompt)\\\"\\s*:\\s*([^\\\"\\s,\\{\\}\\[\\]][^,\\{\\}\\[\\]]*)");

    private JsonResponseParserSupport() {
    }

    static String unwrapCodeFence(String content) {
        if (!StringUtils.hasText(content)) {
            return null;
        }
        Matcher matcher = FENCED_JSON_PATTERN.matcher(content);
        return matcher.matches() ? matcher.group(1).trim() : null;
    }

    /** 返回第一个完整且引号感知的 JSON 对象或数组，不使用贪婪正则。 */
    static String extractFirstBalancedJson(String content) {
        if (!StringUtils.hasText(content)) {
            return null;
        }
        int start = -1;
        int objectDepth = 0;
        int arrayDepth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int index = 0; index < content.length(); index++) {
            char character = content.charAt(index);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (character == '\\' && inString) {
                escaped = true;
                continue;
            }
            if (character == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (start < 0 && (character == '{' || character == '[')) {
                start = index;
            }
            if (start < 0) {
                continue;
            }
            if (character == '{') {
                objectDepth++;
            } else if (character == '}') {
                objectDepth--;
            } else if (character == '[') {
                arrayDepth++;
            } else if (character == ']') {
                arrayDepth--;
            }
            if (objectDepth < 0 || arrayDepth < 0) {
                return null;
            }
            if (objectDepth == 0 && arrayDepth == 0) {
                return content.substring(start, index + 1);
            }
        }
        return null;
    }

    static String sliceFromFirstJsonStart(String content) {
        if (!StringUtils.hasText(content)) {
            return content;
        }
        int objectStart = content.indexOf('{');
        int arrayStart = content.indexOf('[');
        int start = objectStart < 0 ? arrayStart : arrayStart < 0 ? objectStart : Math.min(objectStart, arrayStart);
        return start < 0 ? content.trim() : content.substring(start).trim();
    }

    /** 只改写 JSON 字符串外的中文结构标点；字符串内的正常中文内容完全保留。 */
    static String normalizeStructuralPunctuation(String json) {
        StringBuilder result = new StringBuilder(json.length());
        boolean inString = false;
        boolean escaped = false;
        for (int index = 0; index < json.length(); index++) {
            char character = json.charAt(index);
            if (escaped) {
                result.append(character);
                escaped = false;
            } else if (character == '\\' && inString) {
                result.append(character);
                escaped = true;
            } else if (character == '"') {
                result.append(character);
                inString = !inString;
            } else if (!inString && character == '，') {
                result.append(',');
            } else if (!inString && character == '：') {
                result.append(':');
            } else if (!inString && (character == '“' || character == '”')) {
                result.append('"');
            } else {
                result.append(character);
            }
        }
        return result.toString();
    }

    static String removeTrailingCommas(String json) {
        StringBuilder result = new StringBuilder(json.length());
        boolean inString = false;
        boolean escaped = false;
        for (int index = 0; index < json.length(); index++) {
            char character = json.charAt(index);
            if (escaped) {
                result.append(character);
                escaped = false;
                continue;
            }
            if (character == '\\' && inString) {
                result.append(character);
                escaped = true;
                continue;
            }
            if (character == '"') {
                result.append(character);
                inString = !inString;
                continue;
            }
            if (!inString && character == ',') {
                int next = index + 1;
                while (next < json.length() && Character.isWhitespace(json.charAt(next))) {
                    next++;
                }
                if (next >= json.length() || (next < json.length()
                        && (json.charAt(next) == '}' || json.charAt(next) == ']'))) {
                    continue;
                }
            }
            result.append(character);
        }
        return result.toString();
    }

    static String quoteKnownBareStringValues(String json) {
        Matcher matcher = KNOWN_BARE_VALUE_PATTERN.matcher(json);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String value = matcher.group(2).trim();
            if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value) || "null".equalsIgnoreCase(value)
                    || value.matches("-?\\d+(?:\\.\\d+)?")) {
                matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group()));
            } else {
                matcher.appendReplacement(result, Matcher.quoteReplacement(
                        "\"" + matcher.group(1) + "\": \"" + value + "\""));
            }
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /** 仅在响应结尾缺少闭合结构时补齐，不尝试猜测缺失字段或字符串内容。 */
    static String completeClosingBrackets(String json) {
        StringBuilder closings = new StringBuilder();
        java.util.ArrayDeque<Character> stack = new java.util.ArrayDeque<>();
        boolean inString = false;
        boolean escaped = false;
        for (int index = 0; index < json.length(); index++) {
            char character = json.charAt(index);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (character == '\\' && inString) {
                escaped = true;
                continue;
            }
            if (character == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (character == '{' || character == '[') {
                stack.push(character);
            } else if (character == '}' || character == ']') {
                if (stack.isEmpty() || (character == '}' && stack.pop() != '{')
                        || (character == ']' && stack.pop() != '[')) {
                    return json;
                }
            }
        }
        if (inString || stack.isEmpty()) {
            return json;
        }
        while (!stack.isEmpty()) {
            closings.append(stack.pop() == '{' ? '}' : ']');
        }
        return json + closings;
    }
}
