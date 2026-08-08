package com.chandler.learning.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chandler.learning.agent.domain.dto.AgentSaveRequest;
import com.chandler.learning.agent.domain.entity.AiAgent;
import com.chandler.learning.agent.domain.enums.AiAgentType;
import com.chandler.learning.agent.domain.enums.SystemLogType;
import com.chandler.learning.agent.exception.LearningAssistantException;
import com.chandler.learning.agent.mapper.AiAgentMapper;
import com.chandler.learning.agent.service.learning.SystemLogService;
import com.chandler.learning.agent.service.learning.UserDisplayNameService;
import com.chandler.learning.agent.support.LearningConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.transaction.annotation.Transactional;

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
    private final SystemLogService systemLogService;
    private final UserDisplayNameService userDisplayNameService;

    /**
     * 查询 {@code getByCode} 相关业务。
     */
    public AiAgent getByCode(String code) {
        return agentMapper.selectOne(new LambdaQueryWrapper<AiAgent>()
                .eq(AiAgent::getCode, code)
                .eq(AiAgent::getDeleted, false)
                .last(LearningConstants.SQL_LIMIT_ONE));
    }

    /**
     * 查询 {@code getById} 相关业务。
     */
    public AiAgent getById(Long id) {
        return agentMapper.selectById(id);
    }

    /**
     * 查询 {@code list} 相关业务。
     */
    public List<AiAgent> list(String type, boolean enabledOnly) {
        String normalizedType = StringUtils.hasText(type) ? AiAgentType.of(type).getCode() : null;
        return agentMapper.selectList(new LambdaQueryWrapper<AiAgent>()
                .eq(StringUtils.hasText(normalizedType), AiAgent::getType, normalizedType)
                .eq(AiAgent::getDeleted, false)
                .eq(enabledOnly, AiAgent::getEnabled, true)
                .orderByAsc(AiAgent::getSequence));
    }

    /**
     * 创建或保存 {@code create} 相关业务。
     */
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
        systemLogService.record(null, SystemLogType.AGENT, "创建 Agent", agent.getName());
        log.info("用户「{}」创建了 Agent「{}」", userDisplayNameService.currentUserName(), agent.getName());
        return agent.getId();
    }

    /**
     * 更新 {@code update} 相关业务。
     */
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
        systemLogService.record(null, SystemLogType.AGENT, "更新 Agent", agent.getName());
        log.info("用户「{}」更新了 Agent「{}」", userDisplayNameService.currentUserName(), agent.getName());
    }

    /**
     * 更新 {@code updateEnabled} 相关业务。
     */
    public void updateEnabled(Long id, boolean enabled) {
        AiAgent agent = requireAgent(id);
        agent.setEnabled(enabled);
        agent.setUpdateTime(LocalDateTime.now());
        agentMapper.updateById(agent);
        systemLogService.record(null, SystemLogType.AGENT, enabled ? "启用 Agent" : "停用 Agent", agent.getName());
        log.info("用户「{}」{}了 Agent「{}」", userDisplayNameService.currentUserName(), enabled ? "启用" : "停用", agent.getName());
    }

    /**
     * 更新 {@code delete} 相关业务。
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        long aliveCount = agentMapper.selectCount(new LambdaQueryWrapper<AiAgent>()
                .eq(AiAgent::getDeleted, false));
        if (aliveCount <= 1) {
            throw LearningAssistantException.badRequest(
                    LearningConstants.ErrorCode.AGENT_LAST_NOT_DELETABLE,
                    "最后一个学习 Agent 不能删除");
        }
        AiAgent agent = requireAgent(id);
        agent.setDeleted(true);
        agent.setUpdateTime(LocalDateTime.now());
        agentMapper.updateById(agent);
        systemLogService.record(null, SystemLogType.AGENT, "删除 Agent", agent.getName());
        log.info("用户「{}」删除了 Agent「{}」", userDisplayNameService.currentUserName(), agent.getName());
    }

    /**
     * 更新 {@code clone} 相关业务。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long clone(Long id) {
        AiAgent source = agentMapper.selectById(id);
        if (source == null || Boolean.TRUE.equals(source.getDeleted())) {
            throw LearningAssistantException.notFound(
                    LearningConstants.ErrorCode.AGENT_NOT_FOUND,
                    "Agent 不存在: " + id);
        }
        AiAgent clone = new AiAgent();
        clone.setName(source.getName() + " 副本");
        clone.setCode(source.getCode() + "-" + LocalDateTime.now().getNano());
        clone.setType(source.getType());
        clone.setIcon(source.getIcon());
        clone.setDescription(source.getDescription());
        clone.setSystemPrompt(source.getSystemPrompt());
        clone.setConcisePrompt(source.getConcisePrompt());
        clone.setWelcomeMessage(source.getWelcomeMessage());
        clone.setModelProvider(source.getModelProvider());
        clone.setModelName(source.getModelName());
        clone.setTemperature(source.getTemperature());
        clone.setMaxTokens(source.getMaxTokens());
        clone.setPresetCommands(source.getPresetCommands());
        clone.setEnabled(source.getEnabled());
        clone.setSequence(source.getSequence());
        clone.setDeleted(false);
        clone.setCreateTime(LocalDateTime.now());
        clone.setUpdateTime(LocalDateTime.now());
        agentMapper.insert(clone);
        systemLogService.record(null, SystemLogType.AGENT, "复制 Agent", source.getName());
        log.info("用户「{}」复制了 Agent「{}」", userDisplayNameService.currentUserName(), source.getName());
        return clone.getId();
    }

    /**
     * 更新 {@code copy} 相关业务。
     */
    private void copy(AgentSaveRequest request, AiAgent agent) {
        agent.setName(request.getName());
        agent.setCode(request.getCode());
        agent.setType(AiAgentType.of(request.getType()).getCode());
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

    /**
     * 处理 {@code requireAgent} 相关业务。
     */
    private AiAgent requireAgent(Long id) {
        AiAgent agent = agentMapper.selectById(id);
        if (agent == null || Boolean.TRUE.equals(agent.getDeleted())) {
            throw LearningAssistantException.notFound(
                    LearningConstants.ErrorCode.AGENT_NOT_FOUND,
                    "Agent 不存在: " + id);
        }
        return agent;
    }
}
