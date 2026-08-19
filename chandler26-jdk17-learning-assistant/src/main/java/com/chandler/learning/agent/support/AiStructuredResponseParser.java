package com.chandler.learning.agent.support;

/**
 * 指定模型的结构化输出解析器。
 * <p>
 * 解析器只负责把模型文本转换为 JSON，不负责业务字段、词汇覆盖率等业务契约校验。
 */
public interface AiStructuredResponseParser {

    String name();

    boolean supports(String provider, String modelName);

    AiStructuredResponseParseResult parse(String content);
}
