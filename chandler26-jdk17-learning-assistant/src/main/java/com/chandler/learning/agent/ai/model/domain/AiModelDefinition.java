package com.chandler.learning.agent.ai.model.domain;

import com.chandler.learning.agent.ai.gateway.protocol.AiRequestAdapterType;
import com.chandler.learning.agent.ai.gateway.protocol.AiResponseParserType;
import com.chandler.learning.agent.exception.LearningAssistantException;
import com.chandler.learning.agent.support.LearningConstants;
import lombok.Getter;
import org.springframework.util.StringUtils;

import java.util.Arrays;

/**
 * 当前产品允许新任务使用的模型目录。
 * <p>
 * 模型枚举同时声明 API 模型 ID、上下文能力和前后处理链路，避免运行时按名称片段猜测。
 */
@Getter
public enum AiModelDefinition {

    DEEPSEEK_V4_FLASH(AiProvider.DEEPSEEK, "deepseek-v4-flash", "DeepSeek V4 Flash",
            1_048_576, 393_216, AiRequestAdapterType.DEEPSEEK_CHAT, AiResponseParserType.DEEPSEEK_JSON),
    DEEPSEEK_V4_PRO(AiProvider.DEEPSEEK, "deepseek-v4-pro", "DeepSeek V4 Pro",
            1_048_576, 393_216, AiRequestAdapterType.DEEPSEEK_CHAT, AiResponseParserType.DEEPSEEK_JSON),
    KIMI_K3(AiProvider.KIMI, "kimi-k3", "Kimi K3",
            1_048_576, 1_048_576, AiRequestAdapterType.KIMI_CHAT, AiResponseParserType.KIMI_JSON),
    KIMI_K2_6(AiProvider.KIMI, "kimi-k2.6", "Kimi K2.6",
            262_144, 32_768, AiRequestAdapterType.KIMI_CHAT, AiResponseParserType.KIMI_JSON),
    KIMI_K2_5(AiProvider.KIMI, "kimi-k2.5", "Kimi K2.5",
            262_144, 32_768, AiRequestAdapterType.KIMI_CHAT, AiResponseParserType.KIMI_JSON);

    private final AiProvider provider;
    private final String apiModelId;
    private final String title;
    private final int contextWindowTokens;
    private final int maxOutputTokens;
    private final AiRequestAdapterType requestAdapter;
    private final AiResponseParserType responseParser;

    AiModelDefinition(AiProvider provider, String apiModelId, String title, int contextWindowTokens,
                      int maxOutputTokens, AiRequestAdapterType requestAdapter,
                      AiResponseParserType responseParser) {
        this.provider = provider;
        this.apiModelId = apiModelId;
        this.title = title;
        this.contextWindowTokens = contextWindowTokens;
        this.maxOutputTokens = maxOutputTokens;
        this.requestAdapter = requestAdapter;
        this.responseParser = responseParser;
    }

    /**
     * 根据供应商和实际 API 模型 ID 解析模型。
     */
    public static AiModelDefinition resolve(String providerCode, String apiModelId) {
        AiProvider provider = AiProvider.of(providerCode);
        if (!StringUtils.hasText(apiModelId)) {
            throw LearningAssistantException.badRequest(LearningConstants.ErrorCode.AI_MODEL_NAME_MISSING);
        }
        return Arrays.stream(values())
                .filter(model -> model.provider == provider && model.apiModelId.equalsIgnoreCase(apiModelId.trim()))
                .findFirst()
                .orElseThrow(() -> LearningAssistantException.badRequest(
                        LearningConstants.ErrorCode.AI_MODEL_UNSUPPORTED,
                        "当前不支持模型：" + provider.getTitle() + " / " + apiModelId));
    }

    /**
     * 判断历史配置是否仍属于当前可调用模型目录。
     */
    public static boolean supports(String providerCode, String apiModelId) {
        if (!StringUtils.hasText(providerCode) || !StringUtils.hasText(apiModelId)) {
            return false;
        }
        return Arrays.stream(values()).anyMatch(model -> model.provider.getCode().equalsIgnoreCase(providerCode.trim())
                && model.apiModelId.equalsIgnoreCase(apiModelId.trim()));
    }
}
