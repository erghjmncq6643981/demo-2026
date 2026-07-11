package com.chandler.learning.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chandler.learning.agent.domain.dto.AiModelConfigResponse;
import com.chandler.learning.agent.domain.dto.AiModelConfigSaveRequest;
import com.chandler.learning.agent.domain.entity.AiModelConfig;
import com.chandler.learning.agent.domain.enums.SystemLogType;
import com.chandler.learning.agent.exception.LearningAssistantException;
import com.chandler.learning.agent.mapper.AiModelConfigMapper;
import com.chandler.learning.agent.security.ApiKeyCryptoService;
import com.chandler.learning.agent.service.learning.SystemLogService;
import com.chandler.learning.agent.service.learning.UserDisplayNameService;
import com.chandler.learning.agent.support.AiModelConnectionConfig;
import com.chandler.learning.agent.support.LearningConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

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
    private final ApiKeyCryptoService apiKeyCryptoService;
    private final SystemLogService systemLogService;
    private final UserDisplayNameService userDisplayNameService;

    public List<AiModelConfigResponse> list(boolean enabledOnly) {
        return modelConfigMapper.selectList(new LambdaQueryWrapper<AiModelConfig>()
                        .eq(AiModelConfig::getDeleted, false)
                        .eq(enabledOnly, AiModelConfig::getEnabled, true)
                        .orderByDesc(AiModelConfig::getIsDefault)
                        .orderByAsc(AiModelConfig::getSequence)
                        .orderByAsc(AiModelConfig::getCreateTime))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public AiModelConfig getById(Long id) {
        return modelConfigMapper.selectOne(new LambdaQueryWrapper<AiModelConfig>()
                .eq(AiModelConfig::getId, id)
                .eq(AiModelConfig::getDeleted, false)
                .last(LearningConstants.SQL_LIMIT_ONE));
    }

    public AiModelConfig getDefaultEnabled() {
        AiModelConfig config = modelConfigMapper.selectOne(new LambdaQueryWrapper<AiModelConfig>()
                .eq(AiModelConfig::getDeleted, false)
                .eq(AiModelConfig::getEnabled, true)
                .eq(AiModelConfig::getIsDefault, true)
                .orderByAsc(AiModelConfig::getSequence)
                .last(LearningConstants.SQL_LIMIT_ONE));
        if (config != null) {
            return config;
        }
        return modelConfigMapper.selectOne(new LambdaQueryWrapper<AiModelConfig>()
                .eq(AiModelConfig::getDeleted, false)
                .eq(AiModelConfig::getEnabled, true)
                .orderByAsc(AiModelConfig::getSequence)
                .orderByAsc(AiModelConfig::getCreateTime)
                .last(LearningConstants.SQL_LIMIT_ONE));
    }

    public AiModelConfigResponse create(AiModelConfigSaveRequest request) {
        if (!StringUtils.hasText(request.getApiKey())) {
            throw LearningAssistantException.badRequest(
                    LearningConstants.ErrorCode.API_KEY_REQUIRED,
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

    public AiModelConfigResponse update(Long id, AiModelConfigSaveRequest request) {
        AiModelConfig config = getById(id);
        if (config == null) {
            throw LearningAssistantException.notFound(
                    LearningConstants.ErrorCode.MODEL_CONFIG_NOT_FOUND,
                    "模型配置不存在: " + id);
        }
        copy(request, config, false);
        config.setUpdateTime(LocalDateTime.now());
        if (Boolean.TRUE.equals(config.getIsDefault())) {
            clearDefault(id);
        }
        modelConfigMapper.updateById(config);
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

    public void updateEnabled(Long id, boolean enabled) {
        AiModelConfig config = getById(id);
        if (config == null) {
            throw LearningAssistantException.notFound(
                    LearningConstants.ErrorCode.MODEL_CONFIG_NOT_FOUND,
                    "模型配置不存在: " + id);
        }
        config.setEnabled(enabled);
        config.setUpdateTime(LocalDateTime.now());
        modelConfigMapper.updateById(config);
        systemLogService.record(null, SystemLogType.AI_MODEL, enabled ? "启用模型配置" : "停用模型配置", config.getName());
        log.info("用户「{}」{}了 AI 模型「{}」",
                userDisplayNameService.currentUserName(),
                enabled ? "启用" : "停用",
                config.getName());
    }

    public void updatePriority(Long id, Integer sequence, Boolean isDefault) {
        AiModelConfig config = getById(id);
        if (config == null) {
            throw LearningAssistantException.notFound(
                    LearningConstants.ErrorCode.MODEL_CONFIG_NOT_FOUND,
                    "模型配置不存在: " + id);
        }
        config.setSequence(sequence == null ? LearningConstants.DEFAULT_SEQUENCE : sequence);
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

    public void delete(Long id) {
        AiModelConfig config = getById(id);
        if (config == null) {
            return;
        }
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

    public AiModelConnectionConfig resolveProviderConfig(String provider) {
        AiModelConfig config = findEnabledByProvider(provider);
        if (config != null) {
            return toConnectionConfig(config);
        }
        return null;
    }

    public AiModelConnectionConfig resolveProviderConfig(Long modelConfigId) {
        AiModelConfig config = getById(modelConfigId);
        if (config == null) {
            throw LearningAssistantException.notFound(
                    LearningConstants.ErrorCode.MODEL_CONFIG_NOT_FOUND,
                    "模型配置不存在: " + modelConfigId);
        }
        return toConnectionConfig(config);
    }

    public String resolveDefaultProvider() {
        AiModelConfig config = getDefaultEnabled();
        return config == null ? null : config.getProvider();
    }

    public String resolveDefaultModel(String provider) {
        AiModelConfig config = findEnabledByProvider(provider);
        return config == null ? null : config.getModelName();
    }

    private AiModelConfig findEnabledByProvider(String provider) {
        if (!StringUtils.hasText(provider)) {
            return getDefaultEnabled();
        }
        return modelConfigMapper.selectOne(new LambdaQueryWrapper<AiModelConfig>()
                .eq(AiModelConfig::getDeleted, false)
                .eq(AiModelConfig::getEnabled, true)
                .eq(AiModelConfig::getProvider, provider)
                .orderByDesc(AiModelConfig::getIsDefault)
                .orderByAsc(AiModelConfig::getSequence)
                .last(LearningConstants.SQL_LIMIT_ONE));
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
        config.setName(request.getName().trim());
        config.setProvider(request.getProvider().trim());
        config.setModelName(request.getModelName().trim());
        config.setBaseUrl(request.getBaseUrl().trim());
        config.setChatPath(StringUtils.hasText(request.getChatPath()) ? request.getChatPath().trim() : LearningConstants.DEFAULT_CHAT_PATH);
        if (create || StringUtils.hasText(request.getApiKey())) {
            config.setApiKey(apiKeyCryptoService.encrypt(request.getApiKey().trim()));
        }
        config.setEnabled(Boolean.TRUE.equals(request.getEnabled()));
        config.setIsDefault(Boolean.TRUE.equals(request.getIsDefault()));
        config.setSequence(request.getSequence() == null ? LearningConstants.DEFAULT_SEQUENCE : request.getSequence());
    }

    private void clearDefault(Long keepId) {
        List<AiModelConfig> defaults = modelConfigMapper.selectList(new LambdaQueryWrapper<AiModelConfig>()
                .eq(AiModelConfig::getDeleted, false)
                .eq(AiModelConfig::getIsDefault, true));
        for (AiModelConfig item : defaults) {
            if (keepId != null && keepId.equals(item.getId())) {
                continue;
            }
            item.setIsDefault(false);
            item.setUpdateTime(LocalDateTime.now());
            modelConfigMapper.updateById(item);
        }
    }

    private AiModelConfigResponse toResponse(AiModelConfig config) {
        encryptLegacyApiKey(config);
        AiModelConfigResponse response = new AiModelConfigResponse();
        response.setId(config.getId());
        response.setName(config.getName());
        response.setProvider(config.getProvider());
        response.setModelName(config.getModelName());
        response.setBaseUrl(config.getBaseUrl());
        response.setChatPath(config.getChatPath());
        response.setApiKeyMasked(apiKeyCryptoService.mask(config.getApiKey()));
        response.setEnabled(config.getEnabled());
        response.setIsDefault(config.getIsDefault());
        response.setSequence(config.getSequence());
        response.setCreateTime(config.getCreateTime());
        response.setUpdateTime(config.getUpdateTime());
        return response;
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
