package com.chandler.learning.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chandler.learning.agent.domain.dto.ChatMessageResponse;
import com.chandler.learning.agent.domain.dto.ChatSessionResponse;
import com.chandler.learning.agent.domain.entity.AiChatMessage;
import com.chandler.learning.agent.domain.entity.AiChatSession;
import com.chandler.learning.agent.domain.entity.learning.LearningUser;
import com.chandler.learning.agent.exception.LearningAssistantException;
import com.chandler.learning.agent.mapper.AiChatMessageMapper;
import com.chandler.learning.agent.mapper.AiChatSessionMapper;
import com.chandler.learning.agent.security.LearningUserPrincipal;
import com.chandler.learning.agent.support.LearningConstants;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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

    private final AiChatSessionMapper sessionMapper;
    private final AiChatMessageMapper messageMapper;
    private final ObjectMapper objectMapper;

    public AiChatSession createSession(Long userId, String agentCode, String title, String businessType,
                                       String businessId, String sceneCode, Map<String, Object> variables) {
        Long resolvedUserId = userId == null ? currentUserId() : userId;
        if (resolvedUserId == null) {
            throw LearningAssistantException.unauthorized(
                    LearningConstants.ErrorCode.AUTH_REQUIRED,
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

    public AiChatSession getSession(Long sessionId) {
        if (sessionId == null) {
            return null;
        }
        return sessionMapper.selectOne(new LambdaQueryWrapper<AiChatSession>()
                .eq(AiChatSession::getId, sessionId)
                .eq(AiChatSession::getDeleted, false)
                .last(LearningConstants.SQL_LIMIT_ONE));
    }

    public AiChatSession getOwnedSession(Long userId, Long sessionId) {
        if (userId == null || sessionId == null) {
            return null;
        }
        return sessionMapper.selectOne(new LambdaQueryWrapper<AiChatSession>()
                .eq(AiChatSession::getId, sessionId)
                .eq(AiChatSession::getUserId, userId)
                .eq(AiChatSession::getDeleted, false)
                .last(LearningConstants.SQL_LIMIT_ONE));
    }

    public List<AiChatMessage> getHistory(Long sessionId) {
        return messageMapper.selectList(new LambdaQueryWrapper<AiChatMessage>()
                .eq(AiChatMessage::getSessionId, sessionId)
                .orderByDesc(AiChatMessage::getSequence)
                .last("LIMIT " + LearningConstants.ChatSession.MAX_HISTORY_SIZE))
                .stream()
                .sorted((left, right) -> Integer.compare(left.getSequence(), right.getSequence()))
                .toList();
    }

    public void addUserMessage(Long sessionId, String content) {
        addMessage(sessionId, LearningConstants.ChatSession.ROLE_USER, content, null, null, null, null);
    }

    public void addAssistantMessage(Long sessionId, String content, Integer tokenCount,
                                    Long costTime, String modelProvider, String modelName) {
        addMessage(sessionId, LearningConstants.ChatSession.ROLE_ASSISTANT, content, tokenCount, costTime, modelProvider, modelName);
    }

    public List<ChatSessionResponse> listSessions(Long userId, String agentCode, String businessType,
                                                  String businessId, String sceneCode) {
        Long resolvedUserId = userId == null ? currentUserId() : userId;
        if (resolvedUserId == null) {
            return List.of();
        }
        return sessionMapper.selectList(new LambdaQueryWrapper<AiChatSession>()
                        .eq(AiChatSession::getUserId, resolvedUserId)
                        .eq(StringUtils.hasText(agentCode), AiChatSession::getAgentCode, agentCode)
                        .eq(StringUtils.hasText(businessType), AiChatSession::getBusinessType, businessType)
                        .eq(StringUtils.hasText(businessId), AiChatSession::getBusinessId, businessId)
                        .eq(StringUtils.hasText(sceneCode), AiChatSession::getSceneCode, sceneCode)
                        .eq(AiChatSession::getDeleted, false)
                        .orderByDesc(AiChatSession::getUpdateTime))
                .stream()
                .map(this::toSessionResponse)
                .toList();
    }

    public ChatSessionResponse detail(Long sessionId) {
        AiChatSession session = getOwnedSession(currentUserId(), sessionId);
        return session == null ? null : toSessionResponse(session);
    }

    public List<ChatMessageResponse> listMessages(Long sessionId) {
        Long userId = currentUserId();
        if (userId == null || getOwnedSession(userId, sessionId) == null) {
            throw LearningAssistantException.notFound(
                    LearningConstants.ErrorCode.CHAT_SESSION_NOT_FOUND,
                    "会话不存在: " + sessionId);
        }
        return messageMapper.selectList(new LambdaQueryWrapper<AiChatMessage>()
                        .eq(AiChatMessage::getSessionId, sessionId)
                        .orderByAsc(AiChatMessage::getSequence))
                .stream()
                .map(this::toMessageResponse)
                .toList();
    }

    public void updateTitle(Long sessionId, String title) {
        Long userId = currentUserId();
        if (userId == null || getOwnedSession(userId, sessionId) == null) {
            throw LearningAssistantException.notFound(
                    LearningConstants.ErrorCode.CHAT_SESSION_NOT_FOUND,
                    "会话不存在: " + sessionId);
        }
        AiChatSession session = new AiChatSession();
        session.setId(sessionId);
        session.setTitle(title);
        session.setUpdateTime(LocalDateTime.now());
        sessionMapper.updateById(session);
    }

    public void delete(Long sessionId) {
        Long userId = currentUserId();
        if (userId == null || getOwnedSession(userId, sessionId) == null) {
            throw LearningAssistantException.notFound(
                    LearningConstants.ErrorCode.CHAT_SESSION_NOT_FOUND,
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
        int nextSequence = getNextSequence(sessionId);
        AiChatMessage message = new AiChatMessage();
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setContent(content);
        message.setTokenCount(tokenCount);
        message.setCostTime(costTime);
        message.setModelProvider(modelProvider);
        message.setModelName(modelName);
        message.setSequence(nextSequence);
        message.setCreateTime(LocalDateTime.now());
        messageMapper.insert(message);

        AiChatSession session = new AiChatSession();
        session.setId(sessionId);
        session.setUpdateTime(LocalDateTime.now());
        sessionMapper.updateById(session);
    }

    private int getNextSequence(Long sessionId) {
        AiChatMessage last = messageMapper.selectOne(new LambdaQueryWrapper<AiChatMessage>()
                .eq(AiChatMessage::getSessionId, sessionId)
                .orderByDesc(AiChatMessage::getSequence)
                .last(LearningConstants.SQL_LIMIT_ONE));
        return last == null || last.getSequence() == null
                ? LearningConstants.FIRST_SEQUENCE
                : last.getSequence() + LearningConstants.SEQUENCE_STEP;
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
                .last(LearningConstants.SQL_LIMIT_ONE));
    }

    private String resolveSceneCode(String sceneCode, String businessType, String businessId, String agentCode) {
        if (StringUtils.hasText(sceneCode)) {
            return sceneCode.trim();
        }
        if (LearningConstants.ChatSession.BUSINESS_TYPE_LEARNING.equalsIgnoreCase(StringUtils.hasText(businessType) ? businessType.trim() : "")) {
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
        return LearningConstants.ChatSession.SCENE_ENGLISH_VOCABULARY;
    }

    private String toJson(Map<String, Object> variables) {
        try {
            return objectMapper.writeValueAsString(variables);
        } catch (Exception ex) {
            throw LearningAssistantException.system(
                    LearningConstants.ErrorCode.JSON_SERIALIZE_FAILED,
                    "会话变量序列化失败",
                    ex);
        }
    }
}
