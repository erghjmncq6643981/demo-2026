package com.chandler.learning.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chandler.learning.agent.domain.dto.ChatMessageResponse;
import com.chandler.learning.agent.domain.dto.ChatSessionResponse;
import com.chandler.learning.agent.domain.entity.AiChatMessage;
import com.chandler.learning.agent.domain.entity.AiChatSession;
import com.chandler.learning.agent.mapper.AiChatMessageMapper;
import com.chandler.learning.agent.mapper.AiChatSessionMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
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

    private static final int MAX_HISTORY_SIZE = 20;

    private final AiChatSessionMapper sessionMapper;
    private final AiChatMessageMapper messageMapper;
    private final ObjectMapper objectMapper;

    public AiChatSession createSession(String agentCode, String title, String businessType,
                                       String businessId, Map<String, Object> variables) {
        AiChatSession session = new AiChatSession();
        session.setAgentCode(agentCode);
        session.setBusinessType(businessType);
        session.setBusinessId(businessId);
        session.setTitle(title);
        session.setVariablesJson(variables == null || variables.isEmpty() ? null : toJson(variables));
        session.setDeleted(false);
        session.setCreateTime(LocalDateTime.now());
        session.setUpdateTime(LocalDateTime.now());
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
                .last("LIMIT 1"));
    }

    public List<AiChatMessage> getHistory(Long sessionId) {
        return messageMapper.selectList(new LambdaQueryWrapper<AiChatMessage>()
                .eq(AiChatMessage::getSessionId, sessionId)
                .orderByDesc(AiChatMessage::getSequence)
                .last("LIMIT " + MAX_HISTORY_SIZE))
                .stream()
                .sorted((left, right) -> Integer.compare(left.getSequence(), right.getSequence()))
                .toList();
    }

    public void addUserMessage(Long sessionId, String content) {
        addMessage(sessionId, "user", content, null, null, null, null);
    }

    public void addAssistantMessage(Long sessionId, String content, Integer tokenCount,
                                    Long costTime, String modelProvider, String modelName) {
        addMessage(sessionId, "assistant", content, tokenCount, costTime, modelProvider, modelName);
    }

    public List<ChatSessionResponse> listSessions(String agentCode, String businessType, String businessId) {
        return sessionMapper.selectList(new LambdaQueryWrapper<AiChatSession>()
                        .eq(StringUtils.hasText(agentCode), AiChatSession::getAgentCode, agentCode)
                        .eq(StringUtils.hasText(businessType), AiChatSession::getBusinessType, businessType)
                        .eq(StringUtils.hasText(businessId), AiChatSession::getBusinessId, businessId)
                        .eq(AiChatSession::getDeleted, false)
                        .orderByDesc(AiChatSession::getUpdateTime))
                .stream()
                .map(this::toSessionResponse)
                .toList();
    }

    public ChatSessionResponse detail(Long sessionId) {
        AiChatSession session = getSession(sessionId);
        return session == null ? null : toSessionResponse(session);
    }

    public List<ChatMessageResponse> listMessages(Long sessionId) {
        return messageMapper.selectList(new LambdaQueryWrapper<AiChatMessage>()
                        .eq(AiChatMessage::getSessionId, sessionId)
                        .orderByAsc(AiChatMessage::getSequence))
                .stream()
                .map(this::toMessageResponse)
                .toList();
    }

    public void updateTitle(Long sessionId, String title) {
        AiChatSession session = new AiChatSession();
        session.setId(sessionId);
        session.setTitle(title);
        session.setUpdateTime(LocalDateTime.now());
        sessionMapper.updateById(session);
    }

    public void delete(Long sessionId) {
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
                .last("LIMIT 1"));
        return last == null || last.getSequence() == null ? 1 : last.getSequence() + 1;
    }

    private ChatSessionResponse toSessionResponse(AiChatSession session) {
        ChatSessionResponse response = new ChatSessionResponse();
        response.setId(session.getId());
        response.setAgentCode(session.getAgentCode());
        response.setBusinessType(session.getBusinessType());
        response.setBusinessId(session.getBusinessId());
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

    private String toJson(Map<String, Object> variables) {
        try {
            return objectMapper.writeValueAsString(variables);
        } catch (Exception ex) {
            throw new IllegalArgumentException("会话变量序列化失败", ex);
        }
    }
}
