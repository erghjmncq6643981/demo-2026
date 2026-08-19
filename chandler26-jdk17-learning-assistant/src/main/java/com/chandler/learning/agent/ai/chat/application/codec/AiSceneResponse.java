package com.chandler.learning.agent.ai.chat.application.codec;

import com.chandler.learning.agent.ai.chat.domain.AiInvocationScene;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * 已完成供应商解析和场景根契约校验的 AI 响应。
 *
 * @param invocationScene 场景类型
 * @param root 场景根对象
 * @param normalizedContent 标准 JSON 文本
 * @param parserName 供应商响应解析器
 * @param parseStage 解析成功阶段
 * @param repairs 降级修复动作
 */
public record AiSceneResponse(AiInvocationScene invocationScene,
                              JsonNode root,
                              String normalizedContent,
                              String parserName,
                              String parseStage,
                              List<String> repairs) {
}
