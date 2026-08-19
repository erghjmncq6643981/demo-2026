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
import com.chandler.learning.agent.support.AiModelCapabilityResolver;
import com.chandler.learning.agent.support.AiStructuredResponseParseException;
import com.chandler.learning.agent.support.AiStructuredResponseParseResult;
import com.chandler.learning.agent.support.AiStructuredResponseParserRegistry;
import com.chandler.learning.agent.support.LearningConstants;
import com.chandler.learning.agent.support.PromptRenderer;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
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
    private final AiModelCapabilityResolver modelCapabilityResolver;
    private final AiStructuredResponseParserRegistry structuredResponseParserRegistry;

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
        Long userId = request.getUserId() != null ? request.getUserId() : chatSessionService.currentUserId();
        AiChatSession session = resolveSession(agent, request, userId, startTime, invocationScene);

        AiModelConfig selectedModelConfig = resolveSelectedModelConfig(request.getModelConfigId());
        String provider = resolveProvider(agent, selectedModelConfig);
        String modelName = resolveModelName(agent, provider, selectedModelConfig);
        List<ChatMessageParam> messages = buildMessages(agent, request, session, invocationScene);
        int estimatedInputTokens = estimatePromptTokens(messages);
        int safeContextWindow = validatePromptBudget(invocationScene, provider, modelName, estimatedInputTokens, messages.size());
        ModelChatRequest modelRequest = new ModelChatRequest();
        modelRequest.setInvocationScene(invocationScene);
        modelRequest.setProvider(provider);
        modelRequest.setModel(modelName);
        modelRequest.setModelConfigId(selectedModelConfig == null ? null : selectedModelConfig.getId());
        modelRequest.setTemperature(agent.getTemperature());
        int configuredMaxTokens = agent.getMaxTokens() == null
                ? defaultOutputTokens(invocationScene)
                : agent.getMaxTokens();
        int availableOutputTokens = safeContextWindow - estimatedInputTokens;
        if (availableOutputTokens < LearningConstants.AiContext.MIN_OUTPUT_TOKENS) {
            throw LearningAssistantException.badRequest(
                    LearningConstants.ErrorCode.AI_PROMPT_TOO_LARGE,
                    "本次「" + invocationScene.getTitle() + "」请求没有足够的安全输出空间，请减少输入后重试");
        }
        modelRequest.setMaxTokens(Math.min(configuredMaxTokens, availableOutputTokens));
        if (AiInvocationScene.VOCABULARY_SCENE_UNIT.equals(invocationScene)
                || AiInvocationScene.VOCABULARY_CARD_BATCH.equals(invocationScene)) {
            modelRequest.setFrequencyPenalty(0.3);
            modelRequest.setPresencePenalty(0.1);
        }
        modelRequest.setMessages(messages);
        chatSessionService.addUserMessage(session.getId(), buildUserMessage(request, invocationScene));

        AiModelCallRecord record = buildCallRecord(session.getId(), agent, modelRequest);
        log.info("用户「{}」通过 Agent「{}」向模型「{} / {}」发起「{}」AI 调用，业务类型为「{}」",
                userId != null ? userDisplayNameService.userName(userId) : userDisplayNameService.currentUserName(),
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
            AiStructuredResponseParseResult parsedResponse = validateStructuredResponse(invocationScene, provider,
                    modelName, modelResponse.getContent());
            if (parsedResponse != null) {
                modelResponse.setContent(parsedResponse.normalizedContent());
                modelResponse.setStructuredParser(parsedResponse.parserName());
                modelResponse.setStructuredParseStage(parsedResponse.parseStage());
                modelResponse.setStructuredRepairs(parsedResponse.repairs());
            }
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
            applyParseFailureDiagnostics(record, ex);
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
    private AiChatSession resolveSession(AiAgent agent, AgentChatRequest request, Long userId, long startTime,
                                         AiInvocationScene invocationScene) {
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
                request.getBusinessId(), request.getSceneCode(), requestVariables(request, invocationScene));
    }

    /**
     * 处理 {@code buildMessages} 相关业务。
     */
    private List<ChatMessageParam> buildMessages(AiAgent agent, AgentChatRequest request,
                                                 AiChatSession session, AiInvocationScene invocationScene) {
        List<ChatMessageParam> messages = new ArrayList<>();
        boolean independentAction = invocationScene.independentAction();
        // 固定动作只使用本次请求变量；会话仍然保留用于审计，但不作为模型上下文。
        Map<String, Object> variables = independentAction ? requestVariables(request, invocationScene)
                : readSessionVariables(session);
        if (!independentAction && request.getVariables() != null) {
            variables.putAll(request.getVariables());
        }
        variables.put("USER_QUERY", request.getMessage());

        List<AiChatMessage> history = independentAction ? List.of() : chatSessionService.getHistory(session.getId());
        boolean firstRound = history.isEmpty();
        String systemPrompt = firstRound || !StringUtils.hasText(agent.getConcisePrompt())
                ? agent.getSystemPrompt()
                : agent.getConcisePrompt();
        if (StringUtils.hasText(systemPrompt)) {
            messages.add(new ChatMessageParam(ChatMessageRole.SYSTEM.getCode(), promptRenderer.render(systemPrompt, variables)));
        }

        if (!independentAction) {
            messages.addAll(historyWithinBudget(history));
        }

        messages.add(new ChatMessageParam(ChatMessageRole.USER.getCode(), buildUserMessage(request, invocationScene)));
        return messages;
    }

    /**
     * 用户消息由可选模板和真实提问组成，模板变量在保存消息前完成渲染。
     */
    private String buildUserMessage(AgentChatRequest request, AiInvocationScene invocationScene) {
        StringBuilder userMessage = new StringBuilder();
        if (StringUtils.hasText(request.getTemplateCode())) {
            userMessage.append(promptTemplateService.render(request.getTemplateCode(),
                            requestVariables(request, invocationScene)))
                    .append("\n\n");
        }
        userMessage.append(request.getMessage());
        return userMessage.toString();
    }

    /**
     * 过滤固定动作的输入变量。通用对话属于上下文型动作，保留调用方传入的全部变量。
     */
    private Map<String, Object> requestVariables(AgentChatRequest request, AiInvocationScene invocationScene) {
        Map<String, Object> source = request.getVariables() == null ? Map.of() : request.getVariables();
        if (!invocationScene.independentAction()) {
            return new HashMap<>(source);
        }
        Map<String, Object> compact = new LinkedHashMap<>();
        for (String key : invocationScene.getInputVariableKeys()) {
            if (source.containsKey(key)) {
                compact.put(key, source.get(key));
            }
        }
        return compact;
    }

    /**
     * 发送前估算模型输入 Token。项目不绑定某一家模型的 tokenizer，因此使用保守的字符估算：
     * ASCII 连续文本按 4 字符约 1 Token，非 ASCII 字符按 2 Token 计算。
     */
    private int estimatePromptTokens(List<ChatMessageParam> messages) {
        int estimatedTokens = messages.stream()
                .mapToInt(message -> LearningConstants.AiContext.MESSAGE_OVERHEAD_TOKENS
                        + estimateTokens(message.getContent()))
                .sum();
        return estimatedTokens;
    }

    /**
     * 按实际模型能力而非请求字节数控制上下文。输入和模型最大输出合计触及 90% 前会被拒绝。
     */
    private int validatePromptBudget(AiInvocationScene invocationScene, String provider, String modelName,
                                     int estimatedTokens, int messageCount) {
        int contextWindow = modelCapabilityResolver.contextWindowTokens(provider, modelName);
        int safeLimit = modelCapabilityResolver.safeContextWindowTokens(provider, modelName);
        log.debug("AI 请求上下文估算 invocationScene={} provider={} model={} estimatedTokens={} contextWindow={} safeLimit={} messageCount={}",
                invocationScene.getCode(), provider, modelName, estimatedTokens, contextWindow, safeLimit, messageCount);
        if (estimatedTokens >= safeLimit - LearningConstants.AiContext.MIN_OUTPUT_TOKENS) {
            log.warn("event=ai_context_budget result=rejected invocationScene={} provider={} model={} estimatedTokens={} contextWindow={} safeLimit={}",
                    invocationScene.getCode(), provider, modelName, estimatedTokens, contextWindow, safeLimit);
            throw LearningAssistantException.badRequest(
                    LearningConstants.ErrorCode.AI_PROMPT_TOO_LARGE,
                    "本次「" + invocationScene.getTitle() + "」请求预计约 " + estimatedTokens
                            + " Token，已达到安全上限 " + safeLimit
                            + " Token，请减少词汇批次或补充数据后重试");
        }
        return safeLimit;
    }

    /** 固定大 JSON 动作优先保留更大的输出空间，实际值仍受模型安全窗口约束。 */
    private int defaultOutputTokens(AiInvocationScene invocationScene) {
        return switch (invocationScene) {
            case VOCABULARY_CATALOG_ANALYSIS, VOCABULARY_SCENE_UNIT -> 16_000;
            case VOCABULARY_CARD_BATCH -> 8_000;
            default -> 4_096;
        };
    }

    private int estimateTokens(String content) {
        if (!StringUtils.hasText(content)) {
            return LearningConstants.ZERO;
        }
        int tokens = LearningConstants.ZERO;
        int asciiCharacters = LearningConstants.ZERO;
        for (int index = LearningConstants.ZERO; index < content.length(); index++) {
            char character = content.charAt(index);
            if (character <= 0x7F) {
                asciiCharacters++;
                continue;
            }
            tokens += ceilDivide(asciiCharacters, LearningConstants.AiContext.ASCII_CHARACTERS_PER_TOKEN);
            asciiCharacters = LearningConstants.ZERO;
            tokens += LearningConstants.AiContext.NON_ASCII_TOKENS_PER_CHARACTER;
        }
        return tokens + ceilDivide(asciiCharacters, LearningConstants.AiContext.ASCII_CHARACTERS_PER_TOKEN);
    }

    /** 统一执行模型解析和场景级结构化响应契约，业务服务再校验字段内容和业务覆盖范围。 */
    private AiStructuredResponseParseResult validateStructuredResponse(AiInvocationScene invocationScene,
                                                                        String provider, String modelName,
                                                                        String content) {
        if (!invocationScene.isStructuredResponse()) {
            return null;
        }
        try {
            AiStructuredResponseParseResult result = structuredResponseParserRegistry.parse(invocationScene, provider,
                    modelName, content);
            JsonNode root = result.root();
            if (root == null || !root.isObject()) {
                throw LearningAssistantException.badRequest(LearningConstants.ErrorCode.AI_RESPONSE_PARSE_FAILED);
            }
            List<String> missingFields = new java.util.ArrayList<>();
            for (String field : invocationScene.getRequiredRootFields()) {
                if (root.path(field).isMissingNode() || root.path(field).isNull()) {
                    missingFields.add(field);
                }
            }
            if (!missingFields.isEmpty()) {
                throw LearningAssistantException.badRequest(
                        LearningConstants.ErrorCode.AI_RESPONSE_PARSE_FAILED,
                        "AI 返回内容缺少必要字段：" + String.join("、", missingFields));
            }
            return result;
        } catch (LearningAssistantException ex) {
            throw ex;
        } catch (AiStructuredResponseParseException ex) {
            log.debug("AI structured response parsing failed. provider={} model={} parser={} stage={} repairs={}",
                    provider, modelName, ex.getParserName(), ex.getParseStage(), ex.getRepairs(), ex);
            throw LearningAssistantException.of(LearningConstants.ErrorCode.AI_RESPONSE_PARSE_FAILED, ex);
        } catch (Exception ex) {
            log.debug("AI structured response contract validation failed. provider={} model={} error={}",
                    provider, modelName, ex.getMessage(), ex);
            throw LearningAssistantException.badRequest(LearningConstants.ErrorCode.AI_RESPONSE_PARSE_FAILED);
        }
    }

    private int ceilDivide(int dividend, int divisor) {
        return dividend == LearningConstants.ZERO
                ? LearningConstants.ZERO
                : (dividend + divisor - LearningConstants.SEQUENCE_STEP) / divisor;
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

    /** 将解析器、失败阶段和已尝试修复写入审计摘要，不默认落库原始模型正文。 */
    private void applyParseFailureDiagnostics(AiModelCallRecord record, RuntimeException exception) {
        Throwable cause = exception instanceof LearningAssistantException ? exception.getCause() : exception;
        if (!(cause instanceof AiStructuredResponseParseException parseException)) {
            return;
        }
        Map<String, Object> diagnostic = new LinkedHashMap<>();
        diagnostic.put("contentStored", false);
        diagnostic.put("structuredParser", parseException.getParserName());
        diagnostic.put("structuredParseStage", parseException.getParseStage());
        diagnostic.put("structuredRepairs", parseException.getRepairs());
        diagnostic.put("parseError", limit(parseException.getCause() == null
                ? parseException.getMessage() : parseException.getCause().getMessage(),
                LearningConstants.AiAudit.MAX_ERROR_MESSAGE_LENGTH));
        record.setResponseJson(toJson(diagnostic));
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
        audit.put("finishReason", response.getFinishReason());
        audit.put("structuredParser", response.getStructuredParser());
        audit.put("structuredParseStage", response.getStructuredParseStage());
        audit.put("structuredRepairs", response.getStructuredRepairs());
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
