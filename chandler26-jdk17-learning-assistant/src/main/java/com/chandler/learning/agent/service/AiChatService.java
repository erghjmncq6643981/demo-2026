package com.chandler.learning.agent.service;

import com.chandler.learning.agent.config.AiModelProperties;
import com.chandler.learning.agent.config.AiModelProperties.ProviderConfig;
import com.chandler.learning.agent.domain.dto.AgentChatRequest;
import com.chandler.learning.agent.domain.dto.AgentChatResponse;
import com.chandler.learning.agent.domain.dto.ChatMessageParam;
import com.chandler.learning.agent.domain.dto.ModelChatRequest;
import com.chandler.learning.agent.domain.dto.ModelChatResponse;
import com.chandler.learning.agent.domain.entity.AiAgent;
import com.chandler.learning.agent.domain.entity.AiChatMessage;
import com.chandler.learning.agent.domain.entity.AiChatSession;
import com.chandler.learning.agent.domain.entity.AiModelCallRecord;
import com.chandler.learning.agent.domain.entity.AiModelConfig;
import com.chandler.learning.agent.exception.LearningAssistantException;
import com.chandler.learning.agent.mapper.AiModelCallRecordMapper;
import com.chandler.learning.agent.service.learning.UserDisplayNameService;
import com.chandler.learning.agent.support.AiModelClient;
import com.chandler.learning.agent.support.LearningConstants;
import com.chandler.learning.agent.support.PromptRenderer;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI Agent 对话服务。
 * <p>
 * 负责组装 Agent Prompt、选择模型、调用模型接口，并保存会话消息和模型调用记录。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiChatService {

    private final AiAgentService agentService;
    private final AiPromptTemplateService promptTemplateService;
    private final AiChatSessionService chatSessionService;
    private final AiModelConfigService modelConfigService;
    private final AiModelClient aiModelClient;
    private final AiModelProperties modelProperties;
    private final PromptRenderer promptRenderer;
    private final AiModelCallRecordMapper callRecordMapper;
    private final ObjectMapper objectMapper;
    private final UserDisplayNameService userDisplayNameService;

    public AgentChatResponse chat(AgentChatRequest request) {
        long startTime = System.currentTimeMillis();
        AiAgent agent = getEnabledAgent(request.getAgentCode());
        AiChatSession session = resolveSession(agent, request, startTime);

        List<ChatMessageParam> messages = buildMessages(agent, request, session);
        chatSessionService.addUserMessage(session.getId(), buildUserMessage(request));

        AiModelConfig selectedModelConfig = request.getModelConfigId() == null ? null : modelConfigService.getById(request.getModelConfigId());
        String provider = resolveProvider(agent, selectedModelConfig);
        String modelName = resolveModelName(agent, provider, selectedModelConfig);
        ModelChatRequest modelRequest = new ModelChatRequest();
        modelRequest.setProvider(provider);
        modelRequest.setModel(modelName);
        modelRequest.setModelConfigId(selectedModelConfig == null ? null : selectedModelConfig.getId());
        modelRequest.setTemperature(agent.getTemperature());
        modelRequest.setMaxTokens(agent.getMaxTokens());
        modelRequest.setMessages(messages);

        AiModelCallRecord record = buildCallRecord(session.getId(), agent, modelRequest);
        log.info("用户「{}」通过 Agent「{}」向模型「{} / {}」发起了一次 AI 会话，业务类型为「{}」",
                userDisplayNameService.currentUserName(),
                agent.getName(),
                provider,
                modelName,
                request.getBusinessType());
        log.debug("AI 会话开始 sessionId={} agent={} provider={} model={} messageCount={} businessType={} businessId={}",
                session.getId(),
                agent.getCode(),
                provider,
                modelName,
                messages.size(),
                request.getBusinessType(),
                request.getBusinessId());
        try {
            ModelChatResponse modelResponse = aiModelClient.chat(modelRequest);
            long costTime = System.currentTimeMillis() - startTime;
            chatSessionService.addAssistantMessage(session.getId(), modelResponse.getContent(),
                    modelResponse.getTotalTokens(), costTime, provider, modelName);
            saveSuccessRecord(record, modelResponse, costTime);
            log.debug("AI 会话成功 sessionId={} agent={} provider={} model={} tokens={} cost={}ms",
                    session.getId(),
                    agent.getCode(),
                    provider,
                    modelName,
                    modelResponse.getTotalTokens(),
                    costTime);

            AgentChatResponse response = new AgentChatResponse();
            response.setSessionId(session.getId());
            response.setAgentCode(agent.getCode());
            response.setModelProvider(provider);
            response.setModelName(modelName);
            response.setContent(modelResponse.getContent());
            response.setTokenUsage(modelResponse.getTotalTokens());
            response.setCostTime(costTime);
            return response;
        } catch (RuntimeException ex) {
            long costTime = System.currentTimeMillis() - startTime;
            saveFailedRecord(record, ex, costTime);
            log.error("AI 会话失败 sessionId={} agent={} provider={} model={} cost={}ms error={}",
                    session.getId(),
                    agent.getCode(),
                    provider,
                    modelName,
                    costTime,
                    ex.getMessage(),
                    ex);
            throw ex;
        }
    }

    private AiAgent getEnabledAgent(String agentCode) {
        AiAgent agent = agentService.getByCode(agentCode);
        if (agent == null) {
            throw LearningAssistantException.notFound(
                    LearningConstants.ErrorCode.AGENT_NOT_FOUND,
                    "Agent 不存在: " + agentCode);
        }
        if (!Boolean.TRUE.equals(agent.getEnabled())) {
            throw LearningAssistantException.badRequest(
                    LearningConstants.ErrorCode.AGENT_DISABLED,
                    "Agent 已停用: " + agentCode);
        }
        return agent;
    }

    private AiChatSession resolveSession(AiAgent agent, AgentChatRequest request, long startTime) {
        if (request.getSessionId() != null) {
            AiChatSession session = chatSessionService.getSession(request.getSessionId());
            if (session == null) {
                throw LearningAssistantException.notFound(
                        LearningConstants.ErrorCode.CHAT_SESSION_NOT_FOUND,
                        "会话不存在: " + request.getSessionId());
            }
            return session;
        }

        String title = StringUtils.hasText(request.getTitle())
                ? request.getTitle()
                : agent.getName() + "-" + startTime;
        return chatSessionService.createSession(agent.getCode(), title, request.getBusinessType(),
                request.getBusinessId(), request.getVariables());
    }

    private List<ChatMessageParam> buildMessages(AiAgent agent, AgentChatRequest request, AiChatSession session) {
        List<ChatMessageParam> messages = new ArrayList<>();
        Map<String, Object> variables = readSessionVariables(session);
        if (request.getVariables() != null) {
            variables.putAll(request.getVariables());
        }
        variables.put("USER_QUERY", request.getMessage());

        List<AiChatMessage> history = chatSessionService.getHistory(session.getId());
        boolean firstRound = history.isEmpty();
        String systemPrompt = firstRound || !StringUtils.hasText(agent.getConcisePrompt())
                ? agent.getSystemPrompt()
                : agent.getConcisePrompt();
        if (StringUtils.hasText(systemPrompt)) {
            messages.add(new ChatMessageParam("system", promptRenderer.render(systemPrompt, variables)));
        }

        for (AiChatMessage message : history) {
            if (LearningConstants.ChatSession.ROLE_USER.equals(message.getRole())
                    || LearningConstants.ChatSession.ROLE_ASSISTANT.equals(message.getRole())) {
                messages.add(new ChatMessageParam(message.getRole(), message.getContent()));
            }
        }

        messages.add(new ChatMessageParam(LearningConstants.ChatSession.ROLE_USER, buildUserMessage(request)));
        return messages;
    }

    /**
     * 用户消息由可选模板和真实提问组成，模板变量在保存消息前完成渲染。
     */
    private String buildUserMessage(AgentChatRequest request) {
        StringBuilder userMessage = new StringBuilder();
        if (StringUtils.hasText(request.getTemplateCode())) {
            userMessage.append(promptTemplateService.render(request.getTemplateCode(), request.getVariables()))
                    .append("\n\n");
        }
        userMessage.append(request.getMessage());
        return userMessage.toString();
    }

    private String resolveProvider(AiAgent agent, AiModelConfig selectedModelConfig) {
        if (selectedModelConfig != null) {
            return selectedModelConfig.getProvider();
        }
        if (StringUtils.hasText(agent.getModelProvider())) {
            return agent.getModelProvider();
        }
        return StringUtils.hasText(modelConfigService.resolveDefaultProvider())
                ? modelConfigService.resolveDefaultProvider()
                : modelProperties.getDefaultProvider();
    }

    private String resolveModelName(AiAgent agent, String provider, AiModelConfig selectedModelConfig) {
        if (selectedModelConfig != null) {
            return selectedModelConfig.getModelName();
        }
        if (StringUtils.hasText(agent.getModelName())) {
            return agent.getModelName();
        }
        String configuredModel = modelConfigService.resolveDefaultModel(provider);
        return StringUtils.hasText(configuredModel)
                ? configuredModel
                : fallbackModelName(provider);
    }

    private String fallbackModelName(String provider) {
        ProviderConfig providerConfig = modelProperties.getProvider(provider);
        return providerConfig == null ? null : providerConfig.getDefaultModel();
    }

    private AiModelCallRecord buildCallRecord(Long sessionId, AiAgent agent, ModelChatRequest request) {
        AiModelCallRecord record = new AiModelCallRecord();
        record.setSessionId(sessionId);
        record.setAgentCode(agent.getCode());
        record.setProvider(request.getProvider());
        record.setModelName(request.getModel());
        record.setRequestJson(toJson(request));
        record.setCreateTime(LocalDateTime.now());
        return record;
    }

    private void saveSuccessRecord(AiModelCallRecord record, ModelChatResponse response, long costTime) {
        record.setResponseJson(response.getResponseJson());
        record.setSuccess(true);
        record.setPromptTokens(response.getPromptTokens());
        record.setCompletionTokens(response.getCompletionTokens());
        record.setTotalTokens(response.getTotalTokens());
        record.setLatencyMs(costTime);
        callRecordMapper.insert(record);
    }

    private void saveFailedRecord(AiModelCallRecord record, RuntimeException ex, long costTime) {
        record.setSuccess(false);
        record.setErrorMessage(ex.getMessage());
        record.setLatencyMs(costTime);
        callRecordMapper.insert(record);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw LearningAssistantException.system(
                    LearningConstants.ErrorCode.JSON_SERIALIZE_FAILED,
                    "模型请求序列化失败",
                    ex);
        }
    }

    private Map<String, Object> readSessionVariables(AiChatSession session) {
        if (session == null || !StringUtils.hasText(session.getVariablesJson())) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(session.getVariablesJson(), new TypeReference<>() {
            });
        } catch (Exception ex) {
            return new HashMap<>();
        }
    }
}
