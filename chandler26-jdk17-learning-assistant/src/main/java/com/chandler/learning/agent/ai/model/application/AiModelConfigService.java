package com.chandler.learning.agent.ai.model.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.chandler.learning.agent.ai.agent.application.AiAgentBindingService;
import com.chandler.learning.agent.ai.chat.application.AiModelUsageQueryService;
import com.chandler.learning.agent.ai.model.api.response.AiModelConfigResponse;
import com.chandler.learning.agent.ai.model.api.request.AiModelConfigSaveRequest;
import com.chandler.learning.agent.ai.model.domain.bo.AiModelUsageSummary;
import com.chandler.learning.agent.ai.model.api.response.AiModelOptionResponse;
import com.chandler.learning.agent.ai.agent.domain.entity.AiAgent;
import com.chandler.learning.agent.ai.model.domain.entity.AiModelConfig;
import com.chandler.learning.agent.ai.model.domain.enums.AiModelDefinition;
import com.chandler.learning.agent.system.domain.enums.SystemLogType;
import com.chandler.learning.agent.exception.LearningAssistantException;
import com.chandler.learning.agent.ai.model.infrastructure.mapper.AiModelConfigMapper;
import com.chandler.learning.agent.security.ApiKeyCryptoService;
import com.chandler.learning.agent.system.application.SystemLogService;
import com.chandler.learning.agent.identity.application.UserDisplayNameService;
import com.chandler.learning.agent.ai.gateway.protocol.AiModelConnectionConfig;
import com.chandler.learning.agent.ai.gateway.constant.AiGatewayConstants;
import com.chandler.learning.agent.common.constant.CommonConstants;
import com.chandler.learning.agent.common.exception.LearningErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * AI 模型配置服务。
 * <p>
 * 统一处理模型配置的启停、优先级、默认模型和 API Key 加密存储。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiModelConfigService {

    private final AiModelConfigMapper modelConfigMapper;
    private final AiAgentBindingService agentBindingService;
    private final AiModelUsageQueryService modelUsageQueryService;
    private final ApiKeyCryptoService apiKeyCryptoService;
    private final SystemLogService systemLogService;
    private final UserDisplayNameService userDisplayNameService;

    /** 查询列表AI 模型。 */
    public List<AiModelConfigResponse> list(boolean enabledOnly) {
        Map<String, AiModelUsageSummary> usageByModel = modelUsageQueryService.listUsageSummaries().stream()
                .collect(Collectors.toMap(this::usageKey, Function.identity(), (left, right) -> left));
        Map<Long, List<AiAgent>> agentsByConfig = agentBindingService.groupBoundAgents();
        return modelConfigMapper.selectList(new LambdaQueryWrapper<AiModelConfig>()
                        .eq(AiModelConfig::getDeleted, false)
                        .eq(enabledOnly, AiModelConfig::getEnabled, true)
                        .orderByDesc(AiModelConfig::getIsDefault)
                        .orderByAsc(AiModelConfig::getSequence)
                        .orderByAsc(AiModelConfig::getCreateTime))
                .stream()
                .map(config -> toResponse(config,
                        usageByModel.get(usageKey(config.getProvider(), config.getModelName())),
                        agentsByConfig.getOrDefault(config.getId(), List.of())))
                .toList();
    }

    /**
     * 查询学习界面可选择的启用模型，不返回连接地址、密钥信息和治理指标。
     */
    public List<AiModelOptionResponse> listAvailableOptions() {
        return modelConfigMapper.selectList(new LambdaQueryWrapper<AiModelConfig>()
                        .eq(AiModelConfig::getDeleted, false)
                        .eq(AiModelConfig::getEnabled, true)
                        .orderByDesc(AiModelConfig::getIsDefault)
                        .orderByAsc(AiModelConfig::getSequence))
                .stream()
                .filter(config -> AiModelDefinition.supports(config.getProvider(), config.getModelName()))
                .map(this::toOptionResponse)
                .toList();
    }

    /** 按主键查询配置详情。 */
    public AiModelConfig getById(Long id) {
        if (id == null) {
            return null;
        }
        return modelConfigMapper.selectOne(new LambdaQueryWrapper<AiModelConfig>()
                .eq(AiModelConfig::getId, id)
                .eq(AiModelConfig::getDeleted, false)
                .last(CommonConstants.SQL_LIMIT_ONE));
    }

    /** 批量查询 Agent 绑定的模型配置，避免 Agent 列表产生 N+1 查询。 */
    public Map<Long, AiModelConfig> getByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        return modelConfigMapper.selectList(new LambdaQueryWrapper<AiModelConfig>()
                        .in(AiModelConfig::getId, ids)
                        .eq(AiModelConfig::getDeleted, false))
                .stream()
                .collect(Collectors.toMap(AiModelConfig::getId, Function.identity()));
    }

    /** 返回可以被 Agent 调用的具体模型配置。 */
    public AiModelConfig requireEnabled(Long id) {
        if (id == null) {
            throw LearningAssistantException.badRequest(LearningErrorCode.MODEL_CONFIG_NOT_BOUND);
        }
        AiModelConfig config = getById(id);
        if (config == null) {
            throw LearningAssistantException.notFound(LearningErrorCode.MODEL_CONFIG_NOT_FOUND);
        }
        if (!Boolean.TRUE.equals(config.getEnabled())) {
            throw LearningAssistantException.badRequest(LearningErrorCode.AI_PROVIDER_DISABLED);
        }
        AiModelDefinition.resolve(config.getProvider(), config.getModelName());
        return config;
    }

    /** 查询当前默认且启用的模型配置。 */
    public AiModelConfig getDefaultEnabled() {
        return modelConfigMapper.selectList(new LambdaQueryWrapper<AiModelConfig>()
                .eq(AiModelConfig::getDeleted, false)
                .eq(AiModelConfig::getEnabled, true)
                .orderByDesc(AiModelConfig::getIsDefault)
                .orderByAsc(AiModelConfig::getSequence)
                .orderByAsc(AiModelConfig::getCreateTime))
                .stream()
                .filter(config -> AiModelDefinition.supports(config.getProvider(), config.getModelName()))
                .findFirst()
                .orElse(null);
    }

    /** 创建AI 模型。 */
    @Transactional(rollbackFor = Exception.class)
    public AiModelConfigResponse create(AiModelConfigSaveRequest request) {
        if (!StringUtils.hasText(request.getApiKey())) {
            throw LearningAssistantException.badRequest(
                    LearningErrorCode.API_KEY_REQUIRED,
                    "API Key 不能为空");
        }
        AiModelConfig config = new AiModelConfig();
        copy(request, config, true);
        config.setDeleted(false);
        config.setCreateTime(LocalDateTime.now());
        config.setUpdateTime(LocalDateTime.now());
        if (Boolean.TRUE.equals(config.getIsDefault())) {
            clearDefault(null);
        }
        modelConfigMapper.insert(config);
        if (Boolean.TRUE.equals(config.getEnabled())) {
            bindMatchingUnboundAgents(config);
        }
        systemLogService.record(null, SystemLogType.AI_MODEL, "创建模型配置", config.getName());
        log.info("用户「{}」新增了 AI 模型「{}」，供应商「{}」，明细模型「{}」，状态为「{}」，优先级为 {}",
                userDisplayNameService.currentUserName(),
                config.getName(),
                config.getProvider(),
                config.getModelName(),
                enabledLabel(config.getEnabled()),
                config.getSequence());
        return toResponse(config);
    }

    /** 更新AI 模型。 */
    @Transactional(rollbackFor = Exception.class)
    public AiModelConfigResponse update(Long id, AiModelConfigSaveRequest request) {
        AiModelConfig config = getById(id);
        if (config == null) {
            throw LearningAssistantException.notFound(
                    LearningErrorCode.MODEL_CONFIG_NOT_FOUND,
                    "模型配置不存在: " + id);
        }
        if (!Boolean.TRUE.equals(request.getEnabled())) {
            ensureNotBound(config.getId(), "停用");
        }
        copy(request, config, false);
        config.setUpdateTime(LocalDateTime.now());
        if (Boolean.TRUE.equals(config.getIsDefault())) {
            clearDefault(id);
        }
        modelConfigMapper.updateById(config);
        syncAgentModelSnapshot(config);
        if (Boolean.TRUE.equals(config.getEnabled())) {
            bindMatchingUnboundAgents(config);
        }
        systemLogService.record(null, SystemLogType.AI_MODEL, "更新模型配置", config.getName());
        log.info("用户「{}」更新了 AI 模型「{}」，供应商「{}」，明细模型「{}」，状态为「{}」，优先级为 {}",
                userDisplayNameService.currentUserName(),
                config.getName(),
                config.getProvider(),
                config.getModelName(),
                enabledLabel(config.getEnabled()),
                config.getSequence());
        return toResponse(config);
    }

    /** 更新模型或 Agent 的启用状态。 */
    @Transactional(rollbackFor = Exception.class)
    public void updateEnabled(Long id, boolean enabled) {
        AiModelConfig config = getById(id);
        if (config == null) {
            throw LearningAssistantException.notFound(
                    LearningErrorCode.MODEL_CONFIG_NOT_FOUND,
                    "模型配置不存在: " + id);
        }
        if (enabled) {
            AiModelDefinition.resolve(config.getProvider(), config.getModelName());
        } else {
            ensureNotBound(config.getId(), "停用");
        }
        config.setEnabled(enabled);
        config.setUpdateTime(LocalDateTime.now());
        modelConfigMapper.updateById(config);
        if (enabled) {
            bindMatchingUnboundAgents(config);
        }
        systemLogService.record(null, SystemLogType.AI_MODEL, enabled ? "启用模型配置" : "停用模型配置", config.getName());
        log.info("用户「{}」{}了 AI 模型「{}」",
                userDisplayNameService.currentUserName(),
                enabled ? "启用" : "停用",
                config.getName());
    }

    /** 更新模型配置优先级。 */
    public void updatePriority(Long id, Integer sequence, Boolean isDefault) {
        AiModelConfig config = getById(id);
        if (config == null) {
            throw LearningAssistantException.notFound(
                    LearningErrorCode.MODEL_CONFIG_NOT_FOUND,
                    "模型配置不存在: " + id);
        }
        if (Boolean.TRUE.equals(isDefault)) {
            AiModelDefinition.resolve(config.getProvider(), config.getModelName());
        }
        config.setSequence(sequence == null ? CommonConstants.DEFAULT_SEQUENCE : sequence);
        config.setIsDefault(Boolean.TRUE.equals(isDefault));
        config.setUpdateTime(LocalDateTime.now());
        if (Boolean.TRUE.equals(config.getIsDefault())) {
            clearDefault(id);
        }
        modelConfigMapper.updateById(config);
        systemLogService.record(null, SystemLogType.AI_MODEL, "更新模型优先级", config.getName());
        log.info("用户「{}」把 AI 模型「{}」的优先级调整为 {}，是否默认模型：{}",
                userDisplayNameService.currentUserName(),
                config.getName(),
                config.getSequence(),
                config.getIsDefault());
    }

    /** 删除AI 模型。 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        AiModelConfig config = getById(id);
        if (config == null) {
            return;
        }
        ensureNotBound(config.getId(), "删除");
        config.setDeleted(true);
        config.setUpdateTime(LocalDateTime.now());
        modelConfigMapper.updateById(config);
        systemLogService.record(null, SystemLogType.AI_MODEL, "删除模型配置", config.getName());
        log.info("用户「{}」删除了 AI 模型「{}」，供应商「{}」，明细模型「{}」",
                userDisplayNameService.currentUserName(),
                config.getName(),
                config.getProvider(),
                config.getModelName());
    }

    /** 解析AI 模型所需的有效配置。 */
    public AiModelConnectionConfig resolveProviderConfig(String provider) {
        AiModelConfig config = findEnabledByProvider(provider);
        if (config != null) {
            return toConnectionConfig(config);
        }
        return null;
    }

    /** 解析AI 模型所需的有效配置。 */
    public AiModelConnectionConfig resolveProviderConfig(Long modelConfigId) {
        AiModelConfig config = getById(modelConfigId);
        if (config == null) {
            throw LearningAssistantException.notFound(
                    LearningErrorCode.MODEL_CONFIG_NOT_FOUND,
                    "模型配置不存在: " + modelConfigId);
        }
        if (!Boolean.TRUE.equals(config.getEnabled())) {
            throw LearningAssistantException.badRequest(LearningErrorCode.AI_PROVIDER_DISABLED);
        }
        AiModelDefinition.resolve(config.getProvider(), config.getModelName());
        return toConnectionConfig(config);
    }

    /**
     * 解析模型连接测试所需的配置；测试允许检查已保存但停用的模型。
     */
    public AiModelConnectionConfig resolveProviderConfigForTest(Long modelConfigId) {
        AiModelConfig config = getById(modelConfigId);
        if (config == null) {
            throw LearningAssistantException.notFound(LearningErrorCode.MODEL_CONFIG_NOT_FOUND);
        }
        AiModelDefinition.resolve(config.getProvider(), config.getModelName());
        return toConnectionConfig(config);
    }

    /**
     * 按供应商和模型解析连接配置，确保 Agent 指定的模型与实际 API 接入点一致。
     */
    public AiModelConnectionConfig resolveProviderConfig(String provider, String modelName) {
        return modelConfigMapper.selectList(new LambdaQueryWrapper<AiModelConfig>()
                        .eq(AiModelConfig::getDeleted, false)
                        .eq(AiModelConfig::getEnabled, true)
                        .eq(AiModelConfig::getProvider, provider)
                        .eq(AiModelConfig::getModelName, modelName)
                        .orderByDesc(AiModelConfig::getIsDefault)
                        .orderByAsc(AiModelConfig::getSequence))
                .stream()
                .findFirst()
                .map(this::toConnectionConfig)
                .orElse(null);
    }

    /** 解析AI 模型所需的有效配置。 */
    public String resolveDefaultProvider() {
        AiModelConfig config = getDefaultEnabled();
        return config == null ? null : config.getProvider();
    }

    /** 解析AI 模型所需的有效配置。 */
    public String resolveDefaultModel(String provider) {
        AiModelConfig config = findEnabledByProvider(provider);
        return config == null ? null : config.getModelName();
    }

    private AiModelConfig findEnabledByProvider(String provider) {
        if (!StringUtils.hasText(provider)) {
            return getDefaultEnabled();
        }
        return modelConfigMapper.selectList(new LambdaQueryWrapper<AiModelConfig>()
                .eq(AiModelConfig::getDeleted, false)
                .eq(AiModelConfig::getEnabled, true)
                .eq(AiModelConfig::getProvider, provider)
                .orderByDesc(AiModelConfig::getIsDefault)
                .orderByAsc(AiModelConfig::getSequence))
                .stream()
                .filter(config -> AiModelDefinition.supports(config.getProvider(), config.getModelName()))
                .findFirst()
                .orElse(null);
    }

    private AiModelConnectionConfig toConnectionConfig(AiModelConfig config) {
        encryptLegacyApiKey(config);
        AiModelConnectionConfig connectionConfig = new AiModelConnectionConfig();
        connectionConfig.setEnabled(config.getEnabled());
        connectionConfig.setApiKey(apiKeyCryptoService.decrypt(config.getApiKey()));
        connectionConfig.setBaseUrl(config.getBaseUrl());
        connectionConfig.setChatPath(config.getChatPath());
        connectionConfig.setModelName(config.getModelName());
        return connectionConfig;
    }

    private void copy(AiModelConfigSaveRequest request, AiModelConfig config, boolean create) {
        AiModelDefinition modelDefinition = AiModelDefinition.resolve(request.getProvider(), request.getModelName());
        config.setName(request.getName().trim());
        config.setProvider(modelDefinition.getProvider().getCode());
        config.setModelName(modelDefinition.getApiModelId());
        config.setBaseUrl(request.getBaseUrl().trim());
        config.setChatPath(StringUtils.hasText(request.getChatPath()) ? request.getChatPath().trim() : AiGatewayConstants.DEFAULT_CHAT_PATH);
        if (create || StringUtils.hasText(request.getApiKey())) {
            config.setApiKey(apiKeyCryptoService.encrypt(request.getApiKey().trim()));
        }
        config.setEnabled(Boolean.TRUE.equals(request.getEnabled()));
        config.setIsDefault(Boolean.TRUE.equals(request.getIsDefault()));
        config.setSequence(request.getSequence() == null ? CommonConstants.DEFAULT_SEQUENCE : request.getSequence());
    }

    private void clearDefault(Long keepId) {
        LambdaUpdateWrapper<AiModelConfig> wrapper = new LambdaUpdateWrapper<AiModelConfig>()
                .eq(AiModelConfig::getDeleted, false)
                .eq(AiModelConfig::getIsDefault, true)
                .set(AiModelConfig::getIsDefault, false)
                .set(AiModelConfig::getUpdateTime, LocalDateTime.now());
        if (keepId != null) {
            wrapper.ne(AiModelConfig::getId, keepId);
        }
        modelConfigMapper.update(null, wrapper);
    }

    private AiModelConfigResponse toResponse(AiModelConfig config) {
        return toResponse(config, null, boundAgents(config.getId()));
    }

    private AiModelConfigResponse toResponse(AiModelConfig config, AiModelUsageSummary usage,
                                             List<AiAgent> boundAgents) {
        encryptLegacyApiKey(config);
        AiModelDefinition modelDefinition = modelDefinition(config);
        AiModelConfigResponse response = new AiModelConfigResponse();
        response.setId(config.getId());
        response.setName(config.getName());
        response.setProvider(config.getProvider());
        response.setModelName(config.getModelName());
        response.setSupported(modelDefinition != null);
        if (modelDefinition != null) {
            response.setProviderName(modelDefinition.getProvider().getTitle());
            response.setModelDisplayName(modelDefinition.getTitle());
            response.setApiProtocol(modelDefinition.getProvider().getApiProtocol().getCode());
            response.setRequestAdapter(modelDefinition.getRequestAdapter().getCode());
            response.setResponseParser(modelDefinition.getResponseParser().getCode());
            response.setContextWindowTokens(modelDefinition.getContextWindowTokens());
            response.setMaxOutputTokens(modelDefinition.getMaxOutputTokens());
        }
        response.setBaseUrl(config.getBaseUrl());
        response.setChatPath(config.getChatPath());
        response.setApiKeyMasked(apiKeyCryptoService.mask(config.getApiKey()));
        response.setEnabled(config.getEnabled());
        response.setIsDefault(config.getIsDefault());
        response.setSequence(config.getSequence());
        response.setBoundAgentCount((long) boundAgents.size());
        response.setBoundAgentNames(boundAgents.stream().map(AiAgent::getName).toList());
        response.setCallCount(usage == null ? 0L : usage.getCallCount());
        response.setSuccessCount(usage == null ? 0L : usage.getSuccessCount());
        response.setFailedCount(usage == null ? 0L : usage.getFailedCount());
        response.setTotalTokens(usage == null ? 0L : usage.getTotalTokens());
        response.setAverageLatencyMs(usage == null ? 0L : usage.getAverageLatencyMs());
        response.setLastCallTime(usage == null ? null : usage.getLastCallTime());
        response.setCreateTime(config.getCreateTime());
        response.setUpdateTime(config.getUpdateTime());
        return response;
    }

    private AiModelOptionResponse toOptionResponse(AiModelConfig config) {
        AiModelDefinition modelDefinition = AiModelDefinition.resolve(config.getProvider(), config.getModelName());
        AiModelOptionResponse response = new AiModelOptionResponse();
        response.setId(config.getId());
        response.setName(config.getName());
        response.setProvider(config.getProvider());
        response.setModelName(config.getModelName());
        response.setModelDisplayName(modelDefinition.getTitle());
        response.setContextWindowTokens(modelDefinition.getContextWindowTokens());
        response.setEnabled(config.getEnabled());
        response.setIsDefault(config.getIsDefault());
        response.setSequence(config.getSequence());
        return response;
    }

    private AiModelDefinition modelDefinition(AiModelConfig config) {
        return java.util.Arrays.stream(AiModelDefinition.values())
                .filter(model -> model.getProvider().getCode().equalsIgnoreCase(config.getProvider())
                        && model.getApiModelId().equalsIgnoreCase(config.getModelName()))
                .findFirst()
                .orElse(null);
    }

    private String usageKey(AiModelUsageSummary usage) {
        return usageKey(usage.getProvider(), usage.getModelName());
    }

    private String usageKey(String provider, String modelName) {
        return String.valueOf(provider) + "\u0000" + String.valueOf(modelName);
    }

    private List<AiAgent> boundAgents(Long modelConfigId) {
        return agentBindingService.listBoundAgents(modelConfigId);
    }

    private void ensureNotBound(Long modelConfigId, String operation) {
        List<AiAgent> agents = boundAgents(modelConfigId);
        if (agents.isEmpty()) {
            return;
        }
        String names = agents.stream().map(AiAgent::getName).collect(Collectors.joining("、"));
        throw LearningAssistantException.badRequest(
                LearningErrorCode.MODEL_CONFIG_IN_USE,
                "模型配置正在被 Agent「" + names + "」使用，请先更换 Agent 模型后再" + operation);
    }

    /** 模型型号调整后同步冗余快照；实际调用仍以 model_config_id 指向的配置为准。 */
    private void syncAgentModelSnapshot(AiModelConfig config) {
        agentBindingService.synchronizeModelSnapshot(
                config.getId(), config.getProvider(), config.getModelName());
    }

    /** 首次维护模型时自动接上同型号的内置 Agent，减少新库初始化后的人工补配。 */
    private void bindMatchingUnboundAgents(AiModelConfig config) {
        agentBindingService.bindMatchingUnboundAgents(
                config.getId(), config.getProvider(), config.getModelName());
    }

    private void encryptLegacyApiKey(AiModelConfig config) {
        if (config == null || !StringUtils.hasText(config.getApiKey()) || apiKeyCryptoService.isEncrypted(config.getApiKey())) {
            return;
        }
        config.setApiKey(apiKeyCryptoService.encrypt(config.getApiKey()));
        AiModelConfig update = new AiModelConfig();
        update.setId(config.getId());
        update.setApiKey(config.getApiKey());
        update.setUpdateTime(LocalDateTime.now());
        modelConfigMapper.updateById(update);
        log.debug("历史明文 API Key 已加密 modelConfigId={} fingerprint={}",
                config.getId(),
                apiKeyCryptoService.fingerprint(config.getApiKey()));
    }

    private String enabledLabel(Boolean enabled) {
        return Boolean.TRUE.equals(enabled) ? "启用" : "停用";
    }

}
