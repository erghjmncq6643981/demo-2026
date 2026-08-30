package com.chandler.learning.agent.ai.chat.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chandler.learning.agent.ai.chat.api.response.ChatMessageResponse;
import com.chandler.learning.agent.ai.chat.api.response.ChatSessionResponse;
import com.chandler.learning.agent.ai.chat.api.response.AdminAiSessionDetailResponse;
import com.chandler.learning.agent.ai.chat.api.response.AdminAiSessionPageResponse;
import com.chandler.learning.agent.ai.chat.api.response.AdminAiSessionResponse;
import com.chandler.learning.agent.ai.chat.api.response.AiModelCallRecordResponse;
import com.chandler.learning.agent.ai.chat.domain.entity.AiChatMessage;
import com.chandler.learning.agent.ai.chat.domain.entity.AiChatSession;
import com.chandler.learning.agent.ai.chat.domain.entity.AiModelCallRecord;
import com.chandler.learning.agent.identity.domain.entity.LearningUser;
import com.chandler.learning.agent.ai.chat.domain.enums.ChatMessageRole;
import com.chandler.learning.agent.learning.domain.enums.LearningScene;
import com.chandler.learning.agent.exception.LearningAssistantException;
import com.chandler.learning.agent.ai.chat.infrastructure.mapper.AiChatMessageMapper;
import com.chandler.learning.agent.ai.chat.infrastructure.mapper.AiChatSessionMapper;
import com.chandler.learning.agent.ai.chat.infrastructure.mapper.AiModelCallRecordMapper;
import com.chandler.learning.agent.security.LearningUserPrincipal;
import com.chandler.learning.agent.ai.chat.domain.constant.AiChatConstants;
import com.chandler.learning.agent.common.constant.CommonConstants;
import com.chandler.learning.agent.common.exception.LearningErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * AI 对话会话服务。
 */
@Service
@RequiredArgsConstructor
public class AiChatSessionService {

    private static final int DEFAULT_ADMIN_PAGE = 1;
    private static final int DEFAULT_ADMIN_PAGE_SIZE = 20;
    private static final int MAX_ADMIN_PAGE_SIZE = 100;
    private static final int MAX_SESSION_LIST_SIZE = 100;
    private static final int MAX_ADMIN_DETAIL_MESSAGES = 200;
    private static final int MAX_ADMIN_DETAIL_CALLS = 200;

    private final AiChatSessionMapper sessionMapper;
    private final AiChatMessageMapper messageMapper;
    private final AiModelCallRecordMapper modelCallRecordMapper;
    private final ObjectMapper objectMapper;

    /** 创建 AI 审计会话。 */
    public AiChatSession createSession(Long userId, String agentCode, String title, String businessType,
                                       String businessId, String sceneCode, Map<String, Object> variables) {
        Long resolvedUserId = userId == null ? currentUserId() : userId;
        if (resolvedUserId == null) {
            throw LearningAssistantException.unauthorized(
                    LearningErrorCode.AUTH_REQUIRED,
                    "请先登录");
        }
        String resolvedSceneCode = resolveSceneCode(sceneCode, businessType, businessId, agentCode);
        LocalDateTime now = LocalDateTime.now();
        AiChatSession existing = getLatestActiveSession(resolvedUserId, resolvedSceneCode);
        if (existing != null) {
            existing.setAgentCode(agentCode);
            existing.setBusinessType(businessType);
            existing.setBusinessId(businessId);
            existing.setSceneCode(resolvedSceneCode);
            if (!StringUtils.hasText(existing.getTitle()) && StringUtils.hasText(title)) {
                existing.setTitle(title);
            }
            if (variables != null && !variables.isEmpty()) {
                existing.setVariablesJson(toJson(variables));
            }
            existing.setDeleted(false);
            existing.setUpdateTime(now);
            sessionMapper.updateById(existing);
            return existing;
        }

        AiChatSession session = new AiChatSession();
        session.setUserId(resolvedUserId);
        session.setAgentCode(agentCode);
        session.setBusinessType(businessType);
        session.setBusinessId(businessId);
        session.setSceneCode(resolvedSceneCode);
        session.setTitle(title);
        session.setVariablesJson(variables == null || variables.isEmpty() ? null : toJson(variables));
        session.setDeleted(false);
        session.setCreateTime(now);
        session.setUpdateTime(now);
        sessionMapper.insert(session);
        return session;
    }

    /** 按主键查询 AI 会话。 */
    public AiChatSession getSession(Long sessionId) {
        if (sessionId == null) {
            return null;
        }
        return sessionMapper.selectOne(new LambdaQueryWrapper<AiChatSession>()
                .eq(AiChatSession::getId, sessionId)
                .eq(AiChatSession::getDeleted, false)
                .last(CommonConstants.SQL_LIMIT_ONE));
    }

    /** 查询并校验当前用户拥有的 AI 会话。 */
    public AiChatSession getOwnedSession(Long userId, Long sessionId) {
        if (userId == null || sessionId == null) {
            return null;
        }
        return sessionMapper.selectOne(new LambdaQueryWrapper<AiChatSession>()
                .eq(AiChatSession::getId, sessionId)
                .eq(AiChatSession::getUserId, userId)
                .eq(AiChatSession::getDeleted, false)
                .last(CommonConstants.SQL_LIMIT_ONE));
    }

    /** 查询会话中允许参与连续对话的历史消息。 */
    public List<AiChatMessage> getHistory(Long sessionId) {
        return messageMapper.selectList(new LambdaQueryWrapper<AiChatMessage>()
                .eq(AiChatMessage::getSessionId, sessionId)
                .orderByDesc(AiChatMessage::getSequence)
                .last("LIMIT " + AiChatConstants.MAX_HISTORY_SIZE))
                .stream()
                .sorted((left, right) -> Integer.compare(left.getSequence(), right.getSequence()))
                .toList();
    }

    /** 保存用户输入消息。 */
    public void addUserMessage(Long sessionId, String content) {
        addMessage(sessionId, ChatMessageRole.USER.getCode(), content, null, null, null, null);
    }

    /** 保存 AI 助手回复消息。 */
    public void addAssistantMessage(Long sessionId, String content, Integer tokenCount,
                                    Long costTime, String modelProvider, String modelName) {
        addMessage(sessionId, ChatMessageRole.ASSISTANT.getCode(), content, tokenCount, costTime, modelProvider, modelName);
    }

    /** 查询当前用户的 AI 会话列表。 */
    public List<ChatSessionResponse> listSessions(Long userId, String agentCode, String businessType,
                                                  String businessId, String sceneCode) {
        Long resolvedUserId = userId == null ? currentUserId() : userId;
        if (resolvedUserId == null) {
            return List.of();
        }
        return sessionMapper.selectSessionSummaries(resolvedUserId, agentCode, businessType, businessId, sceneCode);
    }

    /** 查询详情AI 会话。 */
    public ChatSessionResponse detail(Long sessionId) {
        AiChatSession session = getOwnedSession(currentUserId(), sessionId);
        return session == null ? null : toSessionResponse(session);
    }

    /** 查询指定 AI 会话的消息列表。 */
    public List<ChatMessageResponse> listMessages(Long sessionId) {
        Long userId = currentUserId();
        if (userId == null || getOwnedSession(userId, sessionId) == null) {
            throw LearningAssistantException.notFound(
                    LearningErrorCode.CHAT_SESSION_NOT_FOUND,
                    "会话不存在: " + sessionId);
        }
        return messageMapper.selectList(new LambdaQueryWrapper<AiChatMessage>()
                        .eq(AiChatMessage::getSessionId, sessionId)
                        .orderByAsc(AiChatMessage::getSequence))
                .stream()
                .map(this::toMessageResponse)
                .toList();
    }

    /** 更新 AI 会话标题。 */
    public void updateTitle(Long sessionId, String title) {
        Long userId = currentUserId();
        if (userId == null || getOwnedSession(userId, sessionId) == null) {
            throw LearningAssistantException.notFound(
                    LearningErrorCode.CHAT_SESSION_NOT_FOUND,
                    "会话不存在: " + sessionId);
        }
        AiChatSession session = new AiChatSession();
        session.setId(sessionId);
        session.setTitle(title);
        session.setUpdateTime(LocalDateTime.now());
        sessionMapper.updateById(session);
    }

    /** 删除AI 会话。 */
    public void delete(Long sessionId) {
        Long userId = currentUserId();
        if (userId == null || getOwnedSession(userId, sessionId) == null) {
            throw LearningAssistantException.notFound(
                    LearningErrorCode.CHAT_SESSION_NOT_FOUND,
                    "会话不存在: " + sessionId);
        }
        AiChatSession session = new AiChatSession();
        session.setId(sessionId);
        session.setDeleted(true);
        session.setUpdateTime(LocalDateTime.now());
        sessionMapper.updateById(session);
    }

    private void addMessage(Long sessionId, String role, String content, Integer tokenCount,
                            Long costTime, String modelProvider, String modelName) {
        if (sessionId == null) {
            return;
        }
        AiChatMessage message = new AiChatMessage();
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setContent(content);
        message.setTokenCount(tokenCount);
        message.setCostTime(costTime);
        message.setModelProvider(modelProvider);
        message.setModelName(modelName);
        message.setCreateTime(LocalDateTime.now());
        DuplicateKeyException lastConflict = null;
        boolean inserted = false;
        for (int attempt = CommonConstants.FIRST_SEQUENCE;
             attempt <= AiChatConstants.MESSAGE_SEQUENCE_RETRY_COUNT; attempt++) {
            message.setSequence(getNextSequence(sessionId));
            try {
                messageMapper.insert(message);
                inserted = true;
                break;
            } catch (DuplicateKeyException ex) {
                lastConflict = ex;
            }
        }
        if (!inserted) {
            throw LearningAssistantException.of(
                    LearningErrorCode.CHAT_MESSAGE_SEQUENCE_CONFLICT,
                    lastConflict);
        }

        AiChatSession session = new AiChatSession();
        session.setId(sessionId);
        session.setUpdateTime(LocalDateTime.now());
        sessionMapper.updateById(session);
    }

    private int getNextSequence(Long sessionId) {
        return messageMapper.selectNextSequence(sessionId);
    }

    /**
     * 分页查询全部用户的 AI 会话及模型调用指标。
     */
    public AdminAiSessionPageResponse adminPage(String keyword, String sceneCode, String provider,
                                                 Boolean success, Integer page, Integer pageSize) {
        int resolvedPage = page == null ? DEFAULT_ADMIN_PAGE : Math.max(DEFAULT_ADMIN_PAGE, page);
        int resolvedPageSize = pageSize == null ? DEFAULT_ADMIN_PAGE_SIZE
                : Math.max(DEFAULT_ADMIN_PAGE, Math.min(pageSize, MAX_ADMIN_PAGE_SIZE));
        int offset = (resolvedPage - DEFAULT_ADMIN_PAGE) * resolvedPageSize;
        String resolvedKeyword = trimToNull(keyword);
        String resolvedSceneCode = trimToNull(sceneCode);
        String resolvedProvider = trimToNull(provider);

        AdminAiSessionPageResponse response = new AdminAiSessionPageResponse();
        response.setItems(sessionMapper.selectAdminSessionPage(resolvedKeyword, resolvedSceneCode,
                resolvedProvider, success, offset, resolvedPageSize));
        response.setTotal(sessionMapper.countAdminSessions(resolvedKeyword, resolvedSceneCode,
                resolvedProvider, success));
        response.setPage(resolvedPage);
        response.setPageSize(resolvedPageSize);
        return response;
    }

    /**
     * 查询一个 AI 会话的消息与模型调用审计详情。
     */
    public AdminAiSessionDetailResponse adminDetail(Long sessionId) {
        AdminAiSessionResponse session = sessionMapper.selectAdminSession(sessionId);
        if (session == null) {
            throw LearningAssistantException.notFound(LearningErrorCode.CHAT_SESSION_NOT_FOUND);
        }
        List<ChatMessageResponse> messages = messageMapper.selectList(new LambdaQueryWrapper<AiChatMessage>()
                        .eq(AiChatMessage::getSessionId, sessionId)
                        .orderByDesc(AiChatMessage::getCreateTime)
                        .orderByDesc(AiChatMessage::getSequence)
                        .last("LIMIT " + MAX_ADMIN_DETAIL_MESSAGES))
                .stream()
                .map(this::toMessageResponse)
                .toList();
        List<AiModelCallRecordResponse> calls = modelCallRecordMapper.selectList(
                        new LambdaQueryWrapper<AiModelCallRecord>()
                                .eq(AiModelCallRecord::getSessionId, sessionId)
                                .orderByDesc(AiModelCallRecord::getCreateTime)
                                .orderByDesc(AiModelCallRecord::getId)
                                .last("LIMIT " + MAX_ADMIN_DETAIL_CALLS))
                .stream()
                .map(this::toCallRecordResponse)
                .toList();

        AdminAiSessionDetailResponse response = new AdminAiSessionDetailResponse();
        response.setSession(session);
        response.setMessages(messages);
        response.setCalls(calls);
        return response;
    }

    private ChatSessionResponse toSessionResponse(AiChatSession session) {
        ChatSessionResponse response = new ChatSessionResponse();
        response.setId(session.getId());
        response.setUserId(session.getUserId());
        response.setAgentCode(session.getAgentCode());
        response.setBusinessType(session.getBusinessType());
        response.setBusinessId(session.getBusinessId());
        response.setSceneCode(session.getSceneCode());
        response.setTitle(session.getTitle());
        response.setCreateTime(session.getCreateTime());
        response.setUpdateTime(session.getUpdateTime());
        response.setMessageCount(messageMapper.selectCount(new LambdaQueryWrapper<AiChatMessage>()
                .eq(AiChatMessage::getSessionId, session.getId())).intValue());
        return response;
    }

    private ChatMessageResponse toMessageResponse(AiChatMessage message) {
        ChatMessageResponse response = new ChatMessageResponse();
        response.setId(message.getId());
        response.setSessionId(message.getSessionId());
        response.setRole(message.getRole());
        response.setContent(message.getContent());
        response.setTokenCount(message.getTokenCount());
        response.setCostTime(message.getCostTime());
        response.setModelProvider(message.getModelProvider());
        response.setModelName(message.getModelName());
        response.setSequence(message.getSequence());
        response.setCreateTime(message.getCreateTime());
        return response;
    }

    /**
     * 转换模型调用审计记录，避免向管理端返回实体内部字段。
     */
    private AiModelCallRecordResponse toCallRecordResponse(AiModelCallRecord record) {
        AiModelCallRecordResponse response = new AiModelCallRecordResponse();
        response.setId(record.getId());
        response.setAgentCode(record.getAgentCode());
        response.setInvocationSceneCode(record.getInvocationSceneCode());
        response.setProvider(record.getProvider());
        response.setModelName(record.getModelName());
        response.setRequestJson(record.getRequestJson());
        response.setResponseJson(record.getResponseJson());
        response.setSuccess(record.getSuccess());
        response.setErrorMessage(record.getErrorMessage());
        response.setPromptTokens(record.getPromptTokens());
        response.setCompletionTokens(record.getCompletionTokens());
        response.setTotalTokens(record.getTotalTokens());
        response.setLatencyMs(record.getLatencyMs());
        response.setCreateTime(record.getCreateTime());
        return response;
    }

    /**
     * 将空白查询条件归一化为空值。
     */
    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /** 获取当前安全上下文中的用户 ID。 */
    public Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        if (authentication.getPrincipal() instanceof LearningUserPrincipal principal) {
            LearningUser user = principal.user();
            return user == null ? null : user.getId();
        }
        return null;
    }

    private AiChatSession getLatestActiveSession(Long userId, String sceneCode) {
        if (userId == null || !StringUtils.hasText(sceneCode)) {
            return null;
        }
        return sessionMapper.selectOne(new LambdaQueryWrapper<AiChatSession>()
                .eq(AiChatSession::getUserId, userId)
                .eq(AiChatSession::getSceneCode, sceneCode)
                .eq(AiChatSession::getDeleted, false)
                .orderByDesc(AiChatSession::getUpdateTime)
                .last(CommonConstants.SQL_LIMIT_ONE));
    }

    private String resolveSceneCode(String sceneCode, String businessType, String businessId, String agentCode) {
        if (StringUtils.hasText(sceneCode)) {
            return sceneCode.trim();
        }
        if (AiChatConstants.BUSINESS_TYPE_LEARNING.equalsIgnoreCase(StringUtils.hasText(businessType) ? businessType.trim() : "")) {
            if (StringUtils.hasText(businessId)) {
                return businessId.trim();
            }
        }
        if (StringUtils.hasText(businessType) && StringUtils.hasText(businessId)) {
            return businessType.trim() + ":" + businessId.trim();
        }
        if (StringUtils.hasText(businessType)) {
            return businessType.trim();
        }
        if (StringUtils.hasText(agentCode)) {
            return agentCode.trim();
        }
        return LearningScene.ENGLISH_VOCABULARY.getCode();
    }

    private String toJson(Map<String, Object> variables) {
        try {
            return objectMapper.writeValueAsString(variables);
        } catch (Exception ex) {
            throw LearningAssistantException.system(
                    LearningErrorCode.JSON_SERIALIZE_FAILED,
                    "会话变量序列化失败",
                    ex);
        }
    }
}
