package com.chandler.learning.agent.ai.model.application;

import com.chandler.learning.agent.ai.model.api.AiModelConnectionTestResponse;
import com.chandler.learning.agent.ai.gateway.protocol.ChatMessageParam;
import com.chandler.learning.agent.ai.gateway.protocol.ModelChatRequest;
import com.chandler.learning.agent.ai.gateway.protocol.ModelChatResponse;
import com.chandler.learning.agent.ai.model.domain.AiModelConfig;
import com.chandler.learning.agent.ai.chat.domain.AiInvocationScene;
import com.chandler.learning.agent.ai.model.domain.AiModelDefinition;
import com.chandler.learning.agent.system.domain.SystemLogType;
import com.chandler.learning.agent.exception.LearningAssistantException;
import com.chandler.learning.agent.system.application.SystemLogService;
import com.chandler.learning.agent.identity.application.UserDisplayNameService;
import com.chandler.learning.agent.ai.gateway.client.AiModelClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * AI 模型配置连接测试服务。
 * <p>
 * 连接测试是管理诊断动作，不经过 Agent，不创建会话，也不写入模型调用记录。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiModelConnectionTestService {

    /** 普通模型探活只需少量输出；Kimi K3 的 completion token 还包含推理过程。 */
    private static final int BASIC_TEST_MAX_TOKENS = 16;
    private static final int REASONING_TEST_MAX_TOKENS = 64;
    private static final String TEST_MESSAGE = "请仅回复 OK";
    private static final int RESPONSE_PREVIEW_LENGTH = 120;

    private final AiModelConfigService modelConfigService;
    private final AiModelClient aiModelClient;
    private final SystemLogService systemLogService;
    private final UserDisplayNameService userDisplayNameService;

    /**
     * 发送最小探活请求，验证模型配置的地址、密钥、模型和响应解析链路。
     */
    public AiModelConnectionTestResponse test(Long modelConfigId) {
        AiModelConfig config = modelConfigService.getById(modelConfigId);
        if (config == null) {
            return failure(null, null, "模型配置不存在");
        }
        AiModelDefinition definition;
        try {
            definition = AiModelDefinition.resolve(config.getProvider(), config.getModelName());
        } catch (LearningAssistantException ex) {
            return failure(config, null, ex.getMessage());
        }

        ModelChatRequest request = new ModelChatRequest();
        request.setInvocationScene(AiInvocationScene.MODEL_CONNECTION_TEST);
        request.setProvider(definition.getProvider().getCode());
        request.setModel(definition.getApiModelId());
        request.setApiProtocol(definition.getProvider().getApiProtocol());
        request.setRequestAdapter(definition.getRequestAdapter());
        request.setResponseParser(definition.getResponseParser());
        request.setModelContextWindowTokens(definition.getContextWindowTokens());
        request.setEffectiveContextWindowTokens(definition.getContextWindowTokens());
        request.setModelConfigId(config.getId());
        request.setTemperature(0D);
        request.setMaxTokens(definition == AiModelDefinition.KIMI_K3
                ? REASONING_TEST_MAX_TOKENS
                : BASIC_TEST_MAX_TOKENS);
        request.setMessages(List.of(new ChatMessageParam("user", TEST_MESSAGE)));

        long start = System.currentTimeMillis();
        try {
            ModelChatResponse response = aiModelClient.testConnection(request);
            long latency = System.currentTimeMillis() - start;
            String message = "连接成功，模型已返回有效响应";
            recordSystemLog("测试模型连接成功", config.getName());
            log.info("用户「{}」测试 AI 模型「{} / {}」连接成功，耗时 {}ms",
                    userDisplayNameService.currentUserName(), config.getProvider(), config.getModelName(), latency);
            return success(config, latency, message, truncate(response.getContent()));
        } catch (LearningAssistantException ex) {
            long latency = System.currentTimeMillis() - start;
            String message = ex.getMessage() == null ? "模型连接测试失败" : ex.getMessage();
            recordSystemLog("测试模型连接失败", config.getName() + "：" + message);
            log.info("用户「{}」测试 AI 模型「{} / {}」连接失败，耗时 {}ms，原因：{}",
                    userDisplayNameService.currentUserName(), config.getProvider(), config.getModelName(), latency, message);
            return failure(config, latency, message);
        } catch (Exception ex) {
            long latency = System.currentTimeMillis() - start;
            recordSystemLog("测试模型连接失败", config.getName());
            log.debug("AI 模型连接测试出现未预期异常 modelConfigId={} latencyMs={}", config.getId(), latency, ex);
            return failure(config, latency, "模型连接测试失败，请检查 Base URL、API Key 和模型配置");
        }
    }

    private AiModelConnectionTestResponse success(AiModelConfig config, long latency, String message,
                                                   String responsePreview) {
        AiModelConnectionTestResponse result = base(config, latency);
        result.setSuccess(true);
        result.setMessage(message);
        result.setResponsePreview(responsePreview);
        return result;
    }

    private AiModelConnectionTestResponse failure(AiModelConfig config, Long latency, String message) {
        AiModelConnectionTestResponse result = base(config, latency);
        result.setSuccess(false);
        result.setMessage(message);
        return result;
    }

    private AiModelConnectionTestResponse base(AiModelConfig config, Long latency) {
        AiModelConnectionTestResponse result = new AiModelConnectionTestResponse();
        result.setProvider(config == null ? null : config.getProvider());
        result.setModelName(config == null ? null : config.getModelName());
        result.setLatencyMs(latency);
        return result;
    }

    private String truncate(String value) {
        if (value == null || value.length() <= RESPONSE_PREVIEW_LENGTH) {
            return value;
        }
        return value.substring(0, RESPONSE_PREVIEW_LENGTH) + "…";
    }

    private void recordSystemLog(String title, String detail) {
        try {
            systemLogService.record(null, SystemLogType.AI_MODEL, title, detail);
        } catch (Exception ex) {
            log.debug("模型连接测试系统日志写入失败 title={}", title, ex);
        }
    }
}
