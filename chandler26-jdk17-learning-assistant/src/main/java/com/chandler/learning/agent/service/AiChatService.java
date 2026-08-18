package com.chandler.learning.agent.service;

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
import com.chandler.learning.agent.domain.enums.AiInvocationScene;
import com.chandler.learning.agent.domain.enums.ChatMessageRole;
import com.chandler.learning.agent.domain.enums.LearningScene;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
    private final PromptRenderer promptRenderer;
    private final AiModelCallRecordMapper callRecordMapper;
    private final ObjectMapper objectMapper;
    private final UserDisplayNameService userDisplayNameService;

    @Value("${learning.ai.audit.store-content:false}")
    private boolean storeAuditContent;

    @Value("${learning.ai.audit.max-content-length:120000}")
    private int maxAuditContentLength;

    /**
     * 处理 {@code chat} 相关业务。
     */
    public AgentChatResponse chat(AgentChatRequest request) {
        long startTime = System.currentTimeMillis();
        AiInvocationScene invocationScene = request.getInvocationScene() == null
                ? AiInvocationScene.GENERAL_CHAT
                : request.getInvocationScene();
        AiAgent agent = getEnabledAgent(request.getAgentCode());
        AiChatSession session = resolveSession(agent, request, startTime);

        List<ChatMessageParam> messages = buildMessages(agent, request, session);
        chatSessionService.addUserMessage(session.getId(), buildUserMessage(request));

        AiModelConfig selectedModelConfig = resolveSelectedModelConfig(request.getModelConfigId());
        String provider = resolveProvider(agent, selectedModelConfig);
        String modelName = resolveModelName(agent, provider, selectedModelConfig);
        ModelChatRequest modelRequest = new ModelChatRequest();
        modelRequest.setInvocationScene(invocationScene);
        modelRequest.setProvider(provider);
        modelRequest.setModel(modelName);
        modelRequest.setModelConfigId(selectedModelConfig == null ? null : selectedModelConfig.getId());
        modelRequest.setTemperature(agent.getTemperature());
        modelRequest.setMaxTokens(agent.getMaxTokens());
        if (AiInvocationScene.VOCABULARY_SCENE_UNIT.equals(invocationScene)
                || AiInvocationScene.VOCABULARY_CARD_BATCH.equals(invocationScene)) {
            modelRequest.setFrequencyPenalty(0.3);
            modelRequest.setPresencePenalty(0.1);
        }
        modelRequest.setMessages(messages);

        AiModelCallRecord record = buildCallRecord(session.getId(), agent, modelRequest);
        log.info("用户「{}」通过 Agent「{}」向模型「{} / {}」发起「{}」AI 调用，业务类型为「{}」",
                userDisplayNameService.currentUserName(),
                agent.getName(),
                provider,
                modelName,
                invocationScene.getTitle(),
                request.getBusinessType());
        log.debug("AI 会话开始 sessionId={} invocationScene={} agent={} provider={} model={} messageCount={} businessType={} businessId={}",
                session.getId(),
                invocationScene.getCode(),
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
            trySaveSuccessRecord(record, modelResponse, costTime);
            log.info("event=ai_call result=success sessionId={} invocationScene={} agent={} provider={} model={} tokens={} costMs={}",
                    session.getId(),
                    invocationScene.getCode(),
                    agent.getCode(),
                    provider,
                    modelName,
                    modelResponse.getTotalTokens(),
                    costTime);

            AgentChatResponse response = new AgentChatResponse();
            response.setSessionId(session.getId());
            response.setAgentCode(agent.getCode());
            response.setInvocationScene(invocationScene);
            response.setModelProvider(provider);
            response.setModelName(modelName);
            response.setContent(modelResponse.getContent());
            response.setTokenUsage(modelResponse.getTotalTokens());
            response.setCostTime(costTime);
            return response;
        } catch (RuntimeException ex) {
            long costTime = System.currentTimeMillis() - startTime;
            trySaveFailedRecord(record, ex, costTime);
            String errorCode = ex instanceof LearningAssistantException businessException
                    ? businessException.getErrorCode()
                    : LearningConstants.ErrorCode.AI_MODEL_CALL_FAILED.getCode();
            log.warn("event=ai_call result=failed sessionId={} invocationScene={} agent={} provider={} model={} costMs={} errorCode={} message={}",
                    session.getId(),
                    invocationScene.getCode(),
                    agent.getCode(),
                    provider,
                    modelName,
                    costTime,
                    errorCode,
                    ex.getMessage());
            log.debug("AI 调用失败技术堆栈 sessionId={}", session.getId(), ex);
            throw ex;
        }
    }

    /**
     * 查询 {@code getEnabledAgent} 相关业务。
     */
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

    /**
     * 处理 {@code resolveSession} 相关业务。
     */
    private AiChatSession resolveSession(AiAgent agent, AgentChatRequest request, long startTime) {
        Long userId = chatSessionService.currentUserId();
        if (request.getSessionId() != null) {
            AiChatSession session = chatSessionService.getOwnedSession(userId, request.getSessionId());
            if (session == null) {
                throw LearningAssistantException.notFound(
                        LearningConstants.ErrorCode.CHAT_SESSION_NOT_FOUND,
                        "会话不存在: " + request.getSessionId());
            }
            return session;
        }

        String title = StringUtils.hasText(request.getTitle())
                ? request.getTitle()
                : sceneDisplayTitle(request.getSceneCode(), request.getBusinessType(), request.getBusinessId(), agent.getName(), startTime);
        return chatSessionService.createSession(userId, agent.getCode(), title, request.getBusinessType(),
                request.getBusinessId(), request.getSceneCode(), request.getVariables());
    }

    /**
     * 处理 {@code buildMessages} 相关业务。
     */
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
            messages.add(new ChatMessageParam(ChatMessageRole.SYSTEM.getCode(), promptRenderer.render(systemPrompt, variables)));
        }

        messages.addAll(historyWithinBudget(history));

        messages.add(new ChatMessageParam(ChatMessageRole.USER.getCode(), buildUserMessage(request)));
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

    /**
     * 处理 {@code resolveProvider} 相关业务。
     */
    private String resolveProvider(AiAgent agent, AiModelConfig selectedModelConfig) {
        if (selectedModelConfig != null) {
            return selectedModelConfig.getProvider();
        }
        if (StringUtils.hasText(agent.getModelProvider())) {
            return agent.getModelProvider();
        }
        String defaultProvider = modelConfigService.resolveDefaultProvider();
        if (StringUtils.hasText(defaultProvider)) {
            return defaultProvider;
        }
        throw LearningAssistantException.badRequest(
                LearningConstants.ErrorCode.MODEL_CONFIG_NOT_FOUND,
                "未配置可用 AI 模型，请先在个人信息 - Agent管理 - 模型管理中新增并启用模型");
    }

    /**
     * 处理 {@code resolveModelName} 相关业务。
     */
    private String resolveModelName(AiAgent agent, String provider, AiModelConfig selectedModelConfig) {
        if (selectedModelConfig != null) {
            return selectedModelConfig.getModelName();
        }
        if (StringUtils.hasText(agent.getModelName())) {
            return agent.getModelName();
        }
        String configuredModel = modelConfigService.resolveDefaultModel(provider);
        if (StringUtils.hasText(configuredModel)) {
            return configuredModel;
        }
        throw LearningAssistantException.badRequest(
                LearningConstants.ErrorCode.AI_MODEL_NAME_MISSING,
                "未找到可用的 AI 模型明细，请先在个人信息 - Agent管理 - 模型管理中维护模型名称");
    }

    /**
     * 处理 {@code resolveSelectedModelConfig} 相关业务。
     */
    private AiModelConfig resolveSelectedModelConfig(Long modelConfigId) {
        if (modelConfigId == null) {
            return null;
        }
        AiModelConfig selectedModelConfig = modelConfigService.getById(modelConfigId);
        if (selectedModelConfig == null) {
            throw LearningAssistantException.notFound(
                    LearningConstants.ErrorCode.MODEL_CONFIG_NOT_FOUND,
                    "模型配置不存在: " + modelConfigId);
        }
        return selectedModelConfig;
    }

    /**
     * 处理 {@code buildCallRecord} 相关业务。
     */
    private AiModelCallRecord buildCallRecord(Long sessionId, AiAgent agent, ModelChatRequest request) {
        AiModelCallRecord record = new AiModelCallRecord();
        record.setSessionId(sessionId);
        record.setAgentCode(agent.getCode());
        record.setInvocationSceneCode(request.getInvocationScene().getCode());
        record.setProvider(request.getProvider());
        record.setModelName(request.getModel());
        record.setRequestJson(requestAuditJson(request));
        record.setCreateTime(LocalDateTime.now());
        return record;
    }

    /**
     * 创建或保存 {@code saveSuccessRecord} 相关业务。
     */
    private void trySaveSuccessRecord(AiModelCallRecord record, ModelChatResponse response, long costTime) {
        record.setResponseJson(responseAuditJson(response));
        record.setSuccess(true);
        record.setPromptTokens(response.getPromptTokens());
        record.setCompletionTokens(response.getCompletionTokens());
        record.setTotalTokens(response.getTotalTokens());
        record.setLatencyMs(costTime);
        try {
            callRecordMapper.insert(record);
        } catch (RuntimeException ex) {
            log.error("event=ai_audit_persist result=failed sessionId={} phase=success_record message={}",
                    record.getSessionId(), ex.getMessage());
            log.debug("AI 成功调用审计记录落库失败 sessionId={}", record.getSessionId(), ex);
        }
    }

    /**
     * 创建或保存 {@code saveFailedRecord} 相关业务。
     */
    private void trySaveFailedRecord(AiModelCallRecord record, RuntimeException ex, long costTime) {
        record.setSuccess(false);
        record.setErrorMessage(limit(ex.getMessage(), LearningConstants.AiAudit.MAX_ERROR_MESSAGE_LENGTH));
        record.setLatencyMs(costTime);
        try {
            callRecordMapper.insert(record);
        } catch (RuntimeException persistenceException) {
            log.error("event=ai_audit_persist result=failed sessionId={} phase=failure_record message={}",
                    record.getSessionId(), persistenceException.getMessage());
            log.debug("AI 失败调用审计记录落库失败 sessionId={}", record.getSessionId(), persistenceException);
        }
    }

    /**
     * 转换 {@code toJson} 相关业务。
     */
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

    /**
     * 查询 {@code readSessionVariables} 相关业务。
     */
    private Map<String, Object> readSessionVariables(AiChatSession session) {
        if (session == null || !StringUtils.hasText(session.getVariablesJson())) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(session.getVariablesJson(), new TypeReference<>() {
            });
        } catch (Exception ex) {
            throw LearningAssistantException.system(
                    LearningConstants.ErrorCode.JSON_PARSE_FAILED,
                    "AI 会话变量损坏，请新建会话后重试",
                    ex);
        }
    }

    /** 仅携带最近且不超过字符预算的历史消息，避免上下文无限膨胀。 */
    private List<ChatMessageParam> historyWithinBudget(List<AiChatMessage> history) {
        List<ChatMessageParam> selected = new ArrayList<>();
        int remaining = LearningConstants.ChatSession.MAX_HISTORY_CHARS;
        for (int index = history.size() - LearningConstants.SEQUENCE_STEP;
             index >= LearningConstants.ZERO; index--) {
            AiChatMessage message = history.get(index);
            if (!ChatMessageRole.conversational(message.getRole()) || !StringUtils.hasText(message.getContent())) {
                continue;
            }
            String content = message.getContent();
            if (content.length() > remaining) {
                if (selected.isEmpty()) {
                    selected.add(new ChatMessageParam(message.getRole(),
                            content.substring(content.length() - remaining)));
                }
                break;
            }
            selected.add(0, new ChatMessageParam(message.getRole(), content));
            remaining -= content.length();
        }
        return selected;
    }

    /** 构造不含提示词正文的模型请求审计摘要；本地排障可通过配置显式开启正文留存。 */
    private String requestAuditJson(ModelChatRequest request) {
        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("contentStored", storeAuditContent);
        audit.put("invocationScene", request.getInvocationScene() == null
                ? null : request.getInvocationScene().getCode());
        audit.put("provider", request.getProvider());
        audit.put("model", request.getModel());
        audit.put("modelConfigId", request.getModelConfigId());
        audit.put("temperature", request.getTemperature());
        audit.put("maxTokens", request.getMaxTokens());
        List<ChatMessageParam> requestMessages = request.getMessages() == null ? List.of() : request.getMessages();
        audit.put("messages", requestMessages.stream().map(message -> {
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("role", message.getRole());
            summary.put("characters", message.getContent() == null ? LearningConstants.ZERO : message.getContent().length());
            return summary;
        }).toList());
        if (storeAuditContent) {
            audit.put("payload", limit(toJson(request), maxAuditContentLength));
        }
        return toJson(audit);
    }

    /** 构造模型响应审计摘要，默认仅保留长度与 Token 指标。 */
    private String responseAuditJson(ModelChatResponse response) {
        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("contentStored", storeAuditContent);
        audit.put("contentCharacters", response.getContent() == null
                ? LearningConstants.ZERO : response.getContent().length());
        audit.put("promptTokens", response.getPromptTokens());
        audit.put("completionTokens", response.getCompletionTokens());
        audit.put("totalTokens", response.getTotalTokens());
        if (storeAuditContent) {
            audit.put("payload", limit(response.getResponseJson(), maxAuditContentLength));
        }
        return toJson(audit);
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(LearningConstants.ZERO, maxLength);
    }

    /**
     * 处理 {@code sceneDisplayTitle} 相关业务。
     */
    private String sceneDisplayTitle(String sceneCode, String businessType, String businessId, String agentName, long startTime) {
        if (StringUtils.hasText(sceneCode)) {
            LearningScene scene = LearningScene.of(sceneCode);
            if (scene.getCode().equals(sceneCode.trim())) {
                return scene.getTitle();
            }
            return sceneCode.trim();
        }
        if (StringUtils.hasText(businessType) && StringUtils.hasText(businessId)) {
            return businessType + "-" + businessId;
        }
        return agentName + "-" + startTime;
    }
}
