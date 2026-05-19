package com.chandler.learning.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chandler.learning.agent.domain.dto.AgentSaveRequest;
import com.chandler.learning.agent.domain.entity.AiAgent;
import com.chandler.learning.agent.exception.LearningAssistantException;
import com.chandler.learning.agent.mapper.AiAgentMapper;
import com.chandler.learning.agent.service.learning.UserDisplayNameService;
import com.chandler.learning.agent.support.LearningConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI Agent 服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiAgentService {

    private final AiAgentMapper agentMapper;
    private final UserDisplayNameService userDisplayNameService;

    public AiAgent getByCode(String code) {
        return agentMapper.selectOne(new LambdaQueryWrapper<AiAgent>()
                .eq(AiAgent::getCode, code)
                .eq(AiAgent::getDeleted, false)
                .last(LearningConstants.SQL_LIMIT_ONE));
    }

    public AiAgent getById(Long id) {
        return agentMapper.selectById(id);
    }

    public List<AiAgent> list(String type) {
        return agentMapper.selectList(new LambdaQueryWrapper<AiAgent>()
                .eq(StringUtils.hasText(type), AiAgent::getType, type)
                .eq(AiAgent::getDeleted, false)
                .eq(AiAgent::getEnabled, true)
                .orderByAsc(AiAgent::getSequence));
    }

    public Long create(AgentSaveRequest request) {
        AiAgent existing = getByCode(request.getCode());
        if (existing != null) {
            throw LearningAssistantException.badRequest(
                    LearningConstants.ErrorCode.AGENT_CODE_EXISTS,
                    "Agent 编码已存在: " + request.getCode());
        }

        AiAgent agent = new AiAgent();
        copy(request, agent);
        agent.setEnabled(true);
        agent.setDeleted(false);
        agent.setCreateTime(LocalDateTime.now());
        agent.setUpdateTime(LocalDateTime.now());
        agentMapper.insert(agent);
        log.info("用户「{}」创建了 Agent「{}」", userDisplayNameService.currentUserName(), agent.getName());
        return agent.getId();
    }

    public void update(Long id, AgentSaveRequest request) {
        AiAgent agent = agentMapper.selectById(id);
        if (agent == null || Boolean.TRUE.equals(agent.getDeleted())) {
            throw LearningAssistantException.notFound(
                    LearningConstants.ErrorCode.AGENT_NOT_FOUND,
                    "Agent 不存在: " + id);
        }
        AiAgent existing = getByCode(request.getCode());
        if (existing != null && !existing.getId().equals(id)) {
            throw LearningAssistantException.badRequest(
                    LearningConstants.ErrorCode.AGENT_CODE_EXISTS,
                    "Agent 编码已存在: " + request.getCode());
        }

        copy(request, agent);
        agent.setUpdateTime(LocalDateTime.now());
        agentMapper.updateById(agent);
        log.info("用户「{}」更新了 Agent「{}」", userDisplayNameService.currentUserName(), agent.getName());
    }

    public void updateEnabled(Long id, boolean enabled) {
        AiAgent agent = new AiAgent();
        agent.setId(id);
        agent.setEnabled(enabled);
        agent.setUpdateTime(LocalDateTime.now());
        agentMapper.updateById(agent);
        log.info("用户「{}」{}了 Agent#{}", userDisplayNameService.currentUserName(), enabled ? "启用" : "停用", id);
    }

    public void delete(Long id) {
        AiAgent agent = new AiAgent();
        agent.setId(id);
        agent.setDeleted(true);
        agent.setUpdateTime(LocalDateTime.now());
        agentMapper.updateById(agent);
        log.info("用户「{}」删除了 Agent#{}", userDisplayNameService.currentUserName(), id);
    }

    private void copy(AgentSaveRequest request, AiAgent agent) {
        agent.setName(request.getName());
        agent.setCode(request.getCode());
        agent.setType(StringUtils.hasText(request.getType()) ? request.getType() : LearningConstants.DEFAULT_AGENT_TYPE);
        agent.setIcon(request.getIcon());
        agent.setDescription(request.getDescription());
        agent.setSystemPrompt(request.getSystemPrompt());
        agent.setConcisePrompt(request.getConcisePrompt());
        agent.setWelcomeMessage(request.getWelcomeMessage());
        agent.setModelProvider(request.getModelProvider());
        agent.setModelName(request.getModelName());
        agent.setTemperature(request.getTemperature());
        agent.setMaxTokens(request.getMaxTokens());
        agent.setPresetCommands(request.getPresetCommands());
        agent.setSequence(request.getSequence() == null ? LearningConstants.DEFAULT_SEQUENCE : request.getSequence());
    }
}
