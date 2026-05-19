package com.chandler.learning.agent.support;

import com.chandler.learning.agent.config.AiModelProperties;
import com.chandler.learning.agent.config.AiModelProperties.ProviderConfig;
import com.chandler.learning.agent.domain.dto.ChatMessageParam;
import com.chandler.learning.agent.domain.dto.ModelChatRequest;
import com.chandler.learning.agent.domain.dto.ModelChatResponse;
import com.chandler.learning.agent.service.AiModelConfigService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI-compatible Chat Completions 客户端。
 */
@Component
@RequiredArgsConstructor
public class OpenAiCompatibleModelClient implements AiModelClient {

    private final RestTemplate restTemplate;
    private final AiModelProperties properties;
    private final AiModelConfigService modelConfigService;
    private final ObjectMapper objectMapper;

    @Override
    public ModelChatResponse chat(ModelChatRequest request) {
        String provider = StringUtils.hasText(request.getProvider())
                ? request.getProvider()
                : properties.getDefaultProvider();
        ProviderConfig providerConfig = resolveProviderConfig(request, provider);
        if (providerConfig == null) {
            throw new IllegalArgumentException("未配置 AI 供应商: " + provider);
        }
        if (!Boolean.TRUE.equals(providerConfig.getEnabled())) {
            throw new IllegalArgumentException("AI 供应商已禁用: " + provider);
        }
        if (!StringUtils.hasText(providerConfig.getApiKey())) {
            throw new IllegalArgumentException("AI 供应商 API Key 为空: " + provider);
        }
        if (!StringUtils.hasText(providerConfig.getBaseUrl())) {
            throw new IllegalArgumentException("AI 供应商 Base URL 为空: " + provider);
        }

        String model = StringUtils.hasText(request.getModel())
                ? request.getModel()
                : providerConfig.getDefaultModel();
        if (!StringUtils.hasText(model)) {
            throw new IllegalArgumentException("AI 模型名称为空: " + provider);
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("model", model);
        payload.put("messages", toMessagePayload(request.getMessages()));
        if (request.getTemperature() != null) {
            payload.put("temperature", request.getTemperature());
        }
        if (request.getMaxTokens() != null) {
            payload.put("max_tokens", request.getMaxTokens());
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(providerConfig.getApiKey());

        String url = buildUrl(providerConfig);
        String responseBody = restTemplate.postForObject(url, new HttpEntity<>(payload, headers), String.class);
        return parseResponse(responseBody);
    }

    private ProviderConfig resolveProviderConfig(ModelChatRequest request, String provider) {
        if (request.getModelConfigId() != null) {
            return modelConfigService.resolveProviderConfig(request.getModelConfigId());
        }
        return modelConfigService.resolveProviderConfig(provider);
    }

    private List<Map<String, String>> toMessagePayload(List<ChatMessageParam> messages) {
        return messages.stream()
                .map(message -> {
                    Map<String, String> item = new HashMap<>();
                    item.put("role", message.getRole());
                    item.put("content", message.getContent());
                    return item;
                })
                .toList();
    }

    private String buildUrl(ProviderConfig providerConfig) {
        String baseUrl = providerConfig.getBaseUrl();
        String chatPath = StringUtils.hasText(providerConfig.getChatPath())
                ? providerConfig.getChatPath()
                : "/chat/completions";
        if (baseUrl.endsWith("/") && chatPath.startsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1) + chatPath;
        }
        if (!baseUrl.endsWith("/") && !chatPath.startsWith("/")) {
            return baseUrl + "/" + chatPath;
        }
        return baseUrl + chatPath;
    }

    private ModelChatResponse parseResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                throw new IllegalStateException("AI 响应缺少 choices 字段");
            }

            JsonNode firstChoice = choices.get(0);
            JsonNode message = firstChoice.path("message");
            String content = message.isMissingNode() ? null : message.path("content").asText(null);
            if (content == null) {
                content = firstChoice.path("text").asText(null);
            }

            JsonNode usage = root.path("usage");
            ModelChatResponse response = new ModelChatResponse();
            response.setContent(content);
            response.setResponseJson(responseBody);
            if (!usage.isMissingNode()) {
                response.setPromptTokens(readInt(usage, "prompt_tokens"));
                response.setCompletionTokens(readInt(usage, "completion_tokens"));
                response.setTotalTokens(readInt(usage, "total_tokens"));
            }
            return response;
        } catch (Exception ex) {
            throw new IllegalStateException("AI 响应解析失败", ex);
        }
    }

    private Integer readInt(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        return value.isMissingNode() || value.isNull() ? null : value.asInt();
    }
}
