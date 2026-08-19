package com.chandler.learning.agent.ai.chat.application;

import com.chandler.learning.agent.ai.chat.domain.AiInvocationScene;
import com.chandler.learning.agent.ai.chat.application.codec.AiSceneResponse;
import com.chandler.learning.agent.exception.LearningAssistantException;
import com.chandler.learning.agent.support.LearningConstants;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * Agent 对话响应。
 */
@Data
@Schema(name = "Agent 对话响应")
public class AgentChatResponse {

    private Long sessionId;

    private String agentCode;

    private AiInvocationScene invocationScene;

    private String modelProvider;

    private String modelName;

    private String content;

    /** 后端业务链路直接消费的结构化响应，不重复暴露到 HTTP JSON。 */
    @JsonIgnore
    private AiSceneResponse structuredResponse;

    private Integer tokenUsage;

    private Long costTime;

    /** 获取已通过指定场景契约校验的根节点副本。 */
    public JsonNode requireStructuredRoot(AiInvocationScene expectedScene) {
        if (structuredResponse == null
                || structuredResponse.root() == null
                || structuredResponse.invocationScene() != expectedScene) {
            throw LearningAssistantException.badRequest(LearningConstants.ErrorCode.AI_RESPONSE_PARSE_FAILED);
        }
        return structuredResponse.root().deepCopy();
    }
}
