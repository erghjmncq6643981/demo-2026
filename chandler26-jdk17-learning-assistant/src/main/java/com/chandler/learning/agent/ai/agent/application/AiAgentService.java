package com.chandler.learning.agent.ai.agent.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chandler.learning.agent.ai.agent.api.request.AgentSaveRequest;
import com.chandler.learning.agent.ai.agent.domain.entity.AiAgent;
import com.chandler.learning.agent.ai.model.domain.entity.AiModelConfig;
import com.chandler.learning.agent.ai.model.application.AiModelConfigService;
import com.chandler.learning.agent.ai.agent.domain.enums.AiAgentType;
import com.chandler.learning.agent.ai.model.domain.enums.AiModelDefinition;
import com.chandler.learning.agent.system.domain.enums.SystemLogType;
import com.chandler.learning.agent.exception.LearningAssistantException;
import com.chandler.learning.agent.ai.agent.infrastructure.mapper.AiAgentMapper;
import com.chandler.learning.agent.system.application.SystemLogService;
import com.chandler.learning.agent.identity.application.UserDisplayNameService;
import com.chandler.learning.agent.common.constant.CommonConstants;
import com.chandler.learning.agent.common.exception.LearningErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * AI Agent 服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiAgentService {

    private final AiAgentMapper agentMapper;
    private final AiModelConfigService modelConfigService;
    private final SystemLogService systemLogService;
    private final UserDisplayNameService userDisplayNameService;

    /** 按业务编码查询有效配置。 */
    public AiAgent getByCode(String code) {
        return agentMapper.selectOne(new LambdaQueryWrapper<AiAgent>()
                .eq(AiAgent::getCode, code)
                .eq(AiAgent::getDeleted, false)
                .last(CommonConstants.SQL_LIMIT_ONE));
    }

    /** 按主键查询配置详情。 */
    public AiAgent getById(Long id) {
        return enrich(agentMapper.selectById(id));
    }

    /** 查询列表AI Agent。 */
    public List<AiAgent> list(String type, boolean enabledOnly) {
        String normalizedType = StringUtils.hasText(type) ? AiAgentType.of(type).getCode() : null;
        List<AiAgent> agents = agentMapper.selectList(new LambdaQueryWrapper<AiAgent>()
                .eq(StringUtils.hasText(normalizedType), AiAgent::getType, normalizedType)
                .eq(AiAgent::getDeleted, false)
                .eq(enabledOnly, AiAgent::getEnabled, true)
                .orderByAsc(AiAgent::getSequence));
        Map<Long, AiModelConfig> configs = modelConfigService.getByIds(agents.stream()
                .map(AiAgent::getModelConfigId)
                .filter(Objects::nonNull)
                .toList());
        agents.forEach(agent -> enrich(agent, configs.get(agent.getModelConfigId())));
        return enabledOnly
                ? agents.stream().filter(agent -> Boolean.TRUE.equals(agent.getModelConfigEnabled())).toList()
                : agents;
    }

    /** 创建AI Agent。 */
    public Long create(AgentSaveRequest request) {
        AiAgent existing = getByCode(request.getCode());
        if (existing != null) {
            throw LearningAssistantException.badRequest(
                    LearningErrorCode.AGENT_CODE_EXISTS,
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

    /** 更新AI Agent。 */
    public void update(Long id, AgentSaveRequest request) {
        AiAgent agent = agentMapper.selectById(id);
        if (agent == null || Boolean.TRUE.equals(agent.getDeleted())) {
            throw LearningAssistantException.notFound(
                    LearningErrorCode.AGENT_NOT_FOUND,
                    "Agent 不存在: " + id);
        }
        AiAgent existing = getByCode(request.getCode());
        if (existing != null && !existing.getId().equals(id)) {
            throw LearningAssistantException.badRequest(
                    LearningErrorCode.AGENT_CODE_EXISTS,
                    "Agent 编码已存在: " + request.getCode());
        }

        copy(request, agent);
        agent.setUpdateTime(LocalDateTime.now());
        agentMapper.updateById(agent);
        systemLogService.record(null, SystemLogType.AGENT, "更新 Agent", agent.getName());
        log.info("用户「{}」更新了 Agent「{}」", userDisplayNameService.currentUserName(), agent.getName());
    }

    /** 更新模型或 Agent 的启用状态。 */
    public void updateEnabled(Long id, boolean enabled) {
        AiAgent agent = requireAgent(id);
        if (enabled) {
            modelConfigService.requireEnabled(agent.getModelConfigId());
        }
        agent.setEnabled(enabled);
        agent.setUpdateTime(LocalDateTime.now());
        agentMapper.updateById(agent);
        systemLogService.record(null, SystemLogType.AGENT, enabled ? "启用 Agent" : "停用 Agent", agent.getName());
        log.info("用户「{}」{}了 Agent「{}」", userDisplayNameService.currentUserName(), enabled ? "启用" : "停用", agent.getName());
    }

    /** 删除AI Agent。 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        long aliveCount = agentMapper.selectCount(new LambdaQueryWrapper<AiAgent>()
                .eq(AiAgent::getDeleted, false));
        if (aliveCount <= 1) {
            throw LearningAssistantException.badRequest(
                    LearningErrorCode.AGENT_LAST_NOT_DELETABLE,
                    "最后一个学习 Agent 不能删除");
        }
        AiAgent agent = requireAgent(id);
        agent.setDeleted(true);
        agent.setUpdateTime(LocalDateTime.now());
        agentMapper.updateById(agent);
        systemLogService.record(null, SystemLogType.AGENT, "删除 Agent", agent.getName());
        log.info("用户「{}」删除了 Agent「{}」", userDisplayNameService.currentUserName(), agent.getName());
    }

    /** 复制AI Agent。 */
    @Transactional(rollbackFor = Exception.class)
    public Long clone(Long id) {
        AiAgent source = agentMapper.selectById(id);
        if (source == null || Boolean.TRUE.equals(source.getDeleted())) {
            throw LearningAssistantException.notFound(
                    LearningErrorCode.AGENT_NOT_FOUND,
                    "Agent 不存在: " + id);
        }
        modelConfigService.requireEnabled(source.getModelConfigId());
        AiAgent clone = new AiAgent();
        clone.setName(source.getName() + " 副本");
        clone.setCode(source.getCode() + "-" + LocalDateTime.now().getNano());
        clone.setType(source.getType());
        clone.setIcon(source.getIcon());
        clone.setDescription(source.getDescription());
        clone.setSystemPrompt(source.getSystemPrompt());
        clone.setConcisePrompt(source.getConcisePrompt());
        clone.setWelcomeMessage(source.getWelcomeMessage());
        clone.setModelConfigId(source.getModelConfigId());
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

    private void copy(AgentSaveRequest request, AiAgent agent) {
        AiModelConfig modelConfig = modelConfigService.requireEnabled(request.getModelConfigId());
        agent.setName(request.getName());
        agent.setCode(request.getCode());
        agent.setType(AiAgentType.of(request.getType()).getCode());
        agent.setIcon(request.getIcon());
        agent.setDescription(request.getDescription());
        agent.setSystemPrompt(request.getSystemPrompt());
        agent.setConcisePrompt(request.getConcisePrompt());
        agent.setWelcomeMessage(request.getWelcomeMessage());
        agent.setModelConfigId(modelConfig.getId());
        agent.setModelProvider(modelConfig.getProvider());
        agent.setModelName(modelConfig.getModelName());
        agent.setTemperature(request.getTemperature());
        agent.setMaxTokens(request.getMaxTokens());
        agent.setPresetCommands(request.getPresetCommands());
        agent.setSequence(request.getSequence() == null ? CommonConstants.DEFAULT_SEQUENCE : request.getSequence());
    }

    private AiAgent requireAgent(Long id) {
        AiAgent agent = agentMapper.selectById(id);
        if (agent == null || Boolean.TRUE.equals(agent.getDeleted())) {
            throw LearningAssistantException.notFound(
                    LearningErrorCode.AGENT_NOT_FOUND,
                    "Agent 不存在: " + id);
        }
        return agent;
    }

    /** 校验 Agent 存在且已启用，供其他业务域保存配置时调用。 */
    public AiAgent requireEnabled(String code) {
        AiAgent agent = getByCode(code);
        if (agent == null || !Boolean.TRUE.equals(agent.getEnabled())) {
            throw LearningAssistantException.badRequest(LearningErrorCode.AGENT_NOT_FOUND);
        }
        return agent;
    }

    /** 补充模型配置展示状态，避免前端通过厂商和型号字符串猜测绑定关系。 */
    private AiAgent enrich(AiAgent agent) {
        if (agent == null) {
            return null;
        }
        return enrich(agent, modelConfigService.getById(agent.getModelConfigId()));
    }

    private AiAgent enrich(AiAgent agent, AiModelConfig config) {
        if (agent == null) {
            return null;
        }
        agent.setModelConfigName(config == null ? null : config.getName());
        agent.setModelConfigEnabled(config != null
                && Boolean.TRUE.equals(config.getEnabled())
                && AiModelDefinition.supports(config.getProvider(), config.getModelName())
                && !Boolean.TRUE.equals(config.getDeleted()));
        return agent;
    }
}
