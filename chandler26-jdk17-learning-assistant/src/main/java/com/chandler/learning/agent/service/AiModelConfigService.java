package com.chandler.learning.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chandler.learning.agent.config.AiModelProperties;
import com.chandler.learning.agent.config.AiModelProperties.ProviderConfig;
import com.chandler.learning.agent.domain.dto.AiModelConfigResponse;
import com.chandler.learning.agent.domain.dto.AiModelConfigSaveRequest;
import com.chandler.learning.agent.domain.entity.AiModelConfig;
import com.chandler.learning.agent.mapper.AiModelConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 模型配置服务。
 */
@Service
@RequiredArgsConstructor
public class AiModelConfigService {

    private final AiModelConfigMapper modelConfigMapper;
    private final AiModelProperties modelProperties;

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
                .last("LIMIT 1"));
    }

    public AiModelConfig getDefaultEnabled() {
        AiModelConfig config = modelConfigMapper.selectOne(new LambdaQueryWrapper<AiModelConfig>()
                .eq(AiModelConfig::getDeleted, false)
                .eq(AiModelConfig::getEnabled, true)
                .eq(AiModelConfig::getIsDefault, true)
                .orderByAsc(AiModelConfig::getSequence)
                .last("LIMIT 1"));
        if (config != null) {
            return config;
        }
        return modelConfigMapper.selectOne(new LambdaQueryWrapper<AiModelConfig>()
                .eq(AiModelConfig::getDeleted, false)
                .eq(AiModelConfig::getEnabled, true)
                .orderByAsc(AiModelConfig::getSequence)
                .orderByAsc(AiModelConfig::getCreateTime)
                .last("LIMIT 1"));
    }

    public AiModelConfigResponse create(AiModelConfigSaveRequest request) {
        if (!StringUtils.hasText(request.getApiKey())) {
            throw new IllegalArgumentException("API Key 不能为空");
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
        return toResponse(config);
    }

    public AiModelConfigResponse update(Long id, AiModelConfigSaveRequest request) {
        AiModelConfig config = getById(id);
        if (config == null) {
            throw new IllegalArgumentException("模型配置不存在: " + id);
        }
        copy(request, config, false);
        config.setUpdateTime(LocalDateTime.now());
        if (Boolean.TRUE.equals(config.getIsDefault())) {
            clearDefault(id);
        }
        modelConfigMapper.updateById(config);
        return toResponse(config);
    }

    public void updateEnabled(Long id, boolean enabled) {
        AiModelConfig config = getById(id);
        if (config == null) {
            throw new IllegalArgumentException("模型配置不存在: " + id);
        }
        config.setEnabled(enabled);
        config.setUpdateTime(LocalDateTime.now());
        modelConfigMapper.updateById(config);
    }

    public void updatePriority(Long id, Integer sequence, Boolean isDefault) {
        AiModelConfig config = getById(id);
        if (config == null) {
            throw new IllegalArgumentException("模型配置不存在: " + id);
        }
        config.setSequence(sequence == null ? 0 : sequence);
        config.setIsDefault(Boolean.TRUE.equals(isDefault));
        config.setUpdateTime(LocalDateTime.now());
        if (Boolean.TRUE.equals(config.getIsDefault())) {
            clearDefault(id);
        }
        modelConfigMapper.updateById(config);
    }

    public void delete(Long id) {
        AiModelConfig config = getById(id);
        if (config == null) {
            return;
        }
        config.setDeleted(true);
        config.setUpdateTime(LocalDateTime.now());
        modelConfigMapper.updateById(config);
    }

    public ProviderConfig resolveProviderConfig(String provider) {
        AiModelConfig config = findEnabledByProvider(provider);
        if (config != null) {
            return toProviderConfig(config);
        }
        return modelProperties.getProvider(provider);
    }

    public String resolveDefaultProvider() {
        AiModelConfig config = getDefaultEnabled();
        return config == null ? modelProperties.getDefaultProvider() : config.getProvider();
    }

    public String resolveDefaultModel(String provider) {
        AiModelConfig config = findEnabledByProvider(provider);
        if (config != null) {
            return config.getModelName();
        }
        ProviderConfig providerConfig = modelProperties.getProvider(provider);
        return providerConfig == null ? null : providerConfig.getDefaultModel();
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
                .last("LIMIT 1"));
    }

    private ProviderConfig toProviderConfig(AiModelConfig config) {
        ProviderConfig providerConfig = new ProviderConfig();
        providerConfig.setEnabled(config.getEnabled());
        providerConfig.setApiKey(config.getApiKey());
        providerConfig.setBaseUrl(config.getBaseUrl());
        providerConfig.setChatPath(config.getChatPath());
        providerConfig.setDefaultModel(config.getModelName());
        return providerConfig;
    }

    private void copy(AiModelConfigSaveRequest request, AiModelConfig config, boolean create) {
        config.setName(request.getName().trim());
        config.setProvider(request.getProvider().trim());
        config.setModelName(request.getModelName().trim());
        config.setBaseUrl(request.getBaseUrl().trim());
        config.setChatPath(StringUtils.hasText(request.getChatPath()) ? request.getChatPath().trim() : "/chat/completions");
        if (create || StringUtils.hasText(request.getApiKey())) {
            config.setApiKey(request.getApiKey().trim());
        }
        config.setEnabled(Boolean.TRUE.equals(request.getEnabled()));
        config.setIsDefault(Boolean.TRUE.equals(request.getIsDefault()));
        config.setSequence(request.getSequence() == null ? 0 : request.getSequence());
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
        AiModelConfigResponse response = new AiModelConfigResponse();
        response.setId(config.getId());
        response.setName(config.getName());
        response.setProvider(config.getProvider());
        response.setModelName(config.getModelName());
        response.setBaseUrl(config.getBaseUrl());
        response.setChatPath(config.getChatPath());
        response.setApiKeyMasked(mask(config.getApiKey()));
        response.setEnabled(config.getEnabled());
        response.setIsDefault(config.getIsDefault());
        response.setSequence(config.getSequence());
        response.setCreateTime(config.getCreateTime());
        response.setUpdateTime(config.getUpdateTime());
        return response;
    }

    private String mask(String apiKey) {
        if (!StringUtils.hasText(apiKey)) {
            return "";
        }
        if (apiKey.length() <= 10) {
            return "****";
        }
        return apiKey.substring(0, 6) + "****" + apiKey.substring(apiKey.length() - 4);
    }
}
