package com.chandler.learning.agent.ai.gateway.client;

import com.chandler.learning.agent.ai.gateway.protocol.ModelChatRequest;
import com.chandler.learning.agent.ai.gateway.protocol.ModelChatResponse;
import com.chandler.learning.agent.ai.gateway.protocol.AiApiProtocol;
import com.chandler.learning.agent.ai.gateway.adapter.AiModelRequestAdapterRegistry;
import com.chandler.learning.agent.ai.gateway.protocol.AiModelConnectionConfig;
import com.chandler.learning.agent.ai.gateway.protocol.AiPreparedModelRequest;
import com.chandler.learning.agent.exception.LearningAssistantException;
import com.chandler.learning.agent.ai.model.application.AiModelConfigService;
import com.chandler.learning.agent.support.LearningConstants;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * OpenAI-compatible Chat Completions 客户端。
 * <p>
 * 兼容 DeepSeek、Kimi 等提供 Chat Completions 风格接口的模型供应商。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAiCompatibleModelClient implements AiModelClient {

    private static final int MAX_UPSTREAM_MESSAGE_LENGTH = 500;

    private final RestTemplate restTemplate;
    private final AiModelConfigService modelConfigService;
    private final ObjectMapper objectMapper;
    private final AiModelRequestAdapterRegistry requestAdapterRegistry;

    /**
     * 处理 {@code chat} 相关业务。
     */
    @Override
    public ModelChatResponse chat(ModelChatRequest request) {
        return chatInternal(request, false);
    }

    /**
     * 管理端模型连接测试入口，允许测试已保存但停用的配置。
     */
    @Override
    public ModelChatResponse testConnection(ModelChatRequest request) {
        return chatInternal(request, true);
    }

    private ModelChatResponse chatInternal(ModelChatRequest request, boolean connectionTest) {
        String provider = StringUtils.hasText(request.getProvider())
                ? request.getProvider()
                : modelConfigService.resolveDefaultProvider();
        if (!StringUtils.hasText(provider)) {
            throw LearningAssistantException.badRequest(
                    LearningConstants.ErrorCode.MODEL_CONFIG_NOT_FOUND,
                    "未配置可用 AI 模型，请先在个人信息 - Agent管理 - 模型管理中新增并启用模型");
        }
        AiModelConnectionConfig providerConfig = resolveProviderConfig(request, provider, connectionTest);
        if (providerConfig == null) {
            throw LearningAssistantException.badRequest(
                    LearningConstants.ErrorCode.AI_PROVIDER_MISSING,
                    "数据库中未找到可用 AI 模型配置: " + provider);
        }
        if (!connectionTest && !Boolean.TRUE.equals(providerConfig.getEnabled())) {
            throw LearningAssistantException.badRequest(
                    LearningConstants.ErrorCode.AI_PROVIDER_DISABLED,
                    "AI 供应商已禁用: " + provider);
        }
        if (!StringUtils.hasText(providerConfig.getApiKey())) {
            throw LearningAssistantException.badRequest(
                    LearningConstants.ErrorCode.AI_PROVIDER_API_KEY_MISSING,
                    "AI 供应商 API Key 为空: " + provider);
        }
        if (!StringUtils.hasText(providerConfig.getBaseUrl())) {
            throw LearningAssistantException.badRequest(
                    LearningConstants.ErrorCode.AI_PROVIDER_BASE_URL_MISSING,
                    "AI 供应商 Base URL 为空: " + provider);
        }

        String model = StringUtils.hasText(request.getModel())
                ? request.getModel()
                : providerConfig.getModelName();
        if (!StringUtils.hasText(model)) {
            throw LearningAssistantException.badRequest(
                    LearningConstants.ErrorCode.AI_MODEL_NAME_MISSING,
                    "AI 模型名称为空: " + provider);
        }

        AiPreparedModelRequest preparedRequest = requestAdapterRegistry.prepare(request);
        if (preparedRequest.protocol() != AiApiProtocol.OPENAI_CHAT_COMPLETIONS) {
            throw LearningAssistantException.badRequest(
                    LearningConstants.ErrorCode.AI_PROVIDER_UNSUPPORTED,
                    "当前模型客户端不支持 API 协议：" + preparedRequest.protocol().getTitle());
        }
        Map<String, Object> payload = preparedRequest.payload();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(providerConfig.getApiKey());

        String url = buildUrl(providerConfig);
        long startTime = System.currentTimeMillis();
        log.debug("模型 HTTP 请求 provider={} model={} protocol={} requestAdapter={} url={} messages={}",
                provider,
                model,
                preparedRequest.protocol().getCode(),
                preparedRequest.adapterType().getCode(),
                url,
                request.getMessages() == null ? LearningConstants.ModelClient.EMPTY_SIZE : request.getMessages().size());
        String responseBody = callModel(url, payload, headers, provider, model, startTime);
        log.debug("模型 HTTP 响应 provider={} model={} cost={}ms responseCharacters={}",
                provider,
                model,
                System.currentTimeMillis() - startTime,
                responseBody == null ? LearningConstants.ZERO : responseBody.length());
        return parseResponse(responseBody);
    }

    /**
     * 调用外部模型接口，并把供应商 HTTP 错误转换为业务可读的学习助手异常。
     */
    private String callModel(String url, Map<String, Object> payload, HttpHeaders headers,
                             String provider, String model, long startTime) {
        try {
            return restTemplate.postForObject(url, new HttpEntity<>(payload, headers), String.class);
        } catch (RestClientResponseException ex) {
            String upstreamMessage = readUpstreamMessage(ex.getResponseBodyAsString());
            String message = buildModelCallErrorMessage(provider, model, ex.getStatusCode().value(), upstreamMessage);
            log.debug("模型 HTTP 调用失败 provider={} model={} status={} cost={}ms upstreamMessage={}",
                    provider,
                    model,
                    ex.getStatusCode().value(),
                    System.currentTimeMillis() - startTime,
                    upstreamMessage);
            throw LearningAssistantException.externalService(
                    resolveModelCallErrorCode(upstreamMessage),
                    message,
                    ex);
        } catch (ResourceAccessException ex) {
            log.debug("模型 HTTP 网络异常 provider={} model={} cost={}ms error={}",
                    provider,
                    model,
                    System.currentTimeMillis() - startTime,
                    ex.getMessage());
            throw LearningAssistantException.externalService(
                    LearningConstants.ErrorCode.AI_MODEL_CALL_FAILED,
                    "AI 模型连接失败，请稍后重试或切换可用模型",
                    ex);
        }
    }

    /**
     * 处理 {@code resolveProviderConfig} 相关业务。
     */
    private AiModelConnectionConfig resolveProviderConfig(ModelChatRequest request, String provider,
                                                          boolean connectionTest) {
        if (request.getModelConfigId() != null) {
            return connectionTest
                    ? modelConfigService.resolveProviderConfigForTest(request.getModelConfigId())
                    : modelConfigService.resolveProviderConfig(request.getModelConfigId());
        }
        return modelConfigService.resolveProviderConfig(provider, request.getModel());
    }

    /**
     * 处理 {@code buildUrl} 相关业务。
     */
    private String buildUrl(AiModelConnectionConfig providerConfig) {
        String baseUrl = providerConfig.getBaseUrl();
        String chatPath = StringUtils.hasText(providerConfig.getChatPath())
                ? providerConfig.getChatPath()
                : LearningConstants.DEFAULT_CHAT_PATH;
        if (baseUrl.endsWith("/") && chatPath.startsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1) + chatPath;
        }
        if (!baseUrl.endsWith("/") && !chatPath.startsWith("/")) {
            return baseUrl + "/" + chatPath;
        }
        return baseUrl + chatPath;
    }

    /**
     * 处理 {@code parseResponse} 相关业务。
     */
    private ModelChatResponse parseResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                throw LearningAssistantException.system(
                        LearningConstants.ErrorCode.AI_RESPONSE_PARSE_FAILED,
                        "AI 响应缺少 choices 字段",
                        null);
            }

            JsonNode firstChoice = choices.get(LearningConstants.ModelClient.FIRST_CHOICE_INDEX);
            JsonNode message = firstChoice.path("message");
            String content = message.isMissingNode() ? null : message.path("content").asText(null);
            if (content == null) {
                content = firstChoice.path("text").asText(null);
            }
            String finishReason = firstChoice.path("finish_reason").asText(null);
            if ("length".equalsIgnoreCase(finishReason)) {
                throw LearningAssistantException.externalService(
                        LearningConstants.ErrorCode.AI_RESPONSE_PARSE_FAILED,
                        "AI 输出达到长度上限，请减少本次输入后重试",
                        null);
            }
            if ("content_filter".equalsIgnoreCase(finishReason)) {
                throw LearningAssistantException.externalService(
                        LearningConstants.ErrorCode.AI_RESPONSE_PARSE_FAILED,
                        "AI 输出被供应商内容安全策略拦截",
                        null);
            }
            if (!StringUtils.hasText(content)) {
                throw LearningAssistantException.externalService(
                        LearningConstants.ErrorCode.AI_RESPONSE_PARSE_FAILED,
                        "AI 未返回有效内容，请稍后重试或切换模型",
                        null);
            }

            JsonNode usage = root.path("usage");
            ModelChatResponse response = new ModelChatResponse();
            response.setContent(content);
            response.setFinishReason(finishReason);
            response.setResponseJson(responseBody);
            if (!usage.isMissingNode()) {
                response.setPromptTokens(readInt(usage, "prompt_tokens"));
                response.setCompletionTokens(readInt(usage, "completion_tokens"));
                response.setTotalTokens(readInt(usage, "total_tokens"));
            }
            return response;
        } catch (Exception ex) {
            if (ex instanceof LearningAssistantException learningAssistantException) {
                throw learningAssistantException;
            }
            throw LearningAssistantException.system(
                    LearningConstants.ErrorCode.AI_RESPONSE_PARSE_FAILED,
                    "AI 响应解析失败",
                    ex);
        }
    }

    /**
     * 查询 {@code readInt} 相关业务。
     */
    private Integer readInt(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        return value.isMissingNode() || value.isNull() ? null : value.asInt();
    }

    /**
     * 查询 {@code readUpstreamMessage} 相关业务。
     */
    private String readUpstreamMessage(String responseBody) {
        if (!StringUtils.hasText(responseBody)) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            String message = root.path("error").path("message").asText(null);
            if (StringUtils.hasText(message)) {
                return truncate(message);
            }
            message = root.path("message").asText(null);
            return truncate(StringUtils.hasText(message) ? message : responseBody);
        } catch (Exception ex) {
            return truncate(responseBody);
        }
    }

    /**
     * 处理 {@code buildModelCallErrorMessage} 相关业务。
     */
    private String buildModelCallErrorMessage(String provider, String model, int statusCode, String upstreamMessage) {
        if (isInsufficientBalance(upstreamMessage)) {
            return "AI 模型余额不足，请在「个人信息 - Agent管理」切换可用模型，或检查供应商账户余额";
        }
        String reason = StringUtils.hasText(upstreamMessage) ? "，原因：" + upstreamMessage : "";
        return "AI 模型调用失败（" + provider + " / " + model + "，HTTP " + statusCode + "）" + reason;
    }

    /**
     * 处理 {@code resolveModelCallErrorCode} 相关业务。
     */
    private LearningConstants.ErrorCode resolveModelCallErrorCode(String upstreamMessage) {
        return isInsufficientBalance(upstreamMessage)
                ? LearningConstants.ErrorCode.AI_MODEL_BALANCE_INSUFFICIENT
                : LearningConstants.ErrorCode.AI_MODEL_CALL_FAILED;
    }

    /**
     * 判断 {@code isInsufficientBalance} 相关业务。
     */
    private boolean isInsufficientBalance(String upstreamMessage) {
        if (!StringUtils.hasText(upstreamMessage)) {
            return false;
        }
        String normalized = upstreamMessage.toLowerCase();
        return normalized.contains("insufficient balance")
                || normalized.contains("insufficient_balance")
                || normalized.contains("余额不足");
    }

    private String truncate(String value) {
        if (value == null || value.length() <= MAX_UPSTREAM_MESSAGE_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_UPSTREAM_MESSAGE_LENGTH);
    }
}
