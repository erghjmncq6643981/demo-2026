package com.chandler.learning.agent.support;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * 结构化模型输出的标准化结果。
 *
 * @param root              已解析的 JSON 根节点
 * @param normalizedContent 标准 JSON 文本，供后续业务服务直接使用
 * @param parserName        实际使用的解析器名称
 * @param parseStage        成功阶段，例如 raw、balanced 或 repaired
 * @param repairs           仅在降级修复时记录的修复项
 */
public record AiStructuredResponseParseResult(JsonNode root, String normalizedContent, String parserName,
                                              String parseStage, List<String> repairs) {
}
