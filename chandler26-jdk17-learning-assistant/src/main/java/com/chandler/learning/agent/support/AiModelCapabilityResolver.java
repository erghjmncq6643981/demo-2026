package com.chandler.learning.agent.support;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * AI 模型能力解析器。
 * <p>
 * 模型上下文长度是 Token 数而不是字节数。对于未显式登记的模型使用保守的 8K 回退值，
 * 避免配置一个未知模型后绕开请求预算保护。
 */
@Component
public class AiModelCapabilityResolver {

    /**
     * 查询模型的总上下文窗口，单位为 Token。
     */
    public int contextWindowTokens(String provider, String modelName) {
        String providerKey = normalize(provider);
        String modelKey = normalize(modelName);
        if ("deepseek".equals(providerKey) || modelKey.startsWith("deepseek-")) {
            return supportedWindow(LearningConstants.AiContext.DEEPSEEK_CONTEXT_WINDOW_TOKENS);
        }
        if ("kimi".equals(providerKey) || "moonshot".equals(providerKey) || modelKey.contains("moonshot")) {
            return supportedWindow(kimiContextWindow(modelKey));
        }
        return LearningConstants.AiContext.DEFAULT_CONTEXT_WINDOW_TOKENS;
    }

    /**
     * 计算达到 90% 前的安全上下文窗口，单位为 Token。
     */
    public int safeContextWindowTokens(String provider, String modelName) {
        return contextWindowTokens(provider, modelName)
                * LearningConstants.AiContext.SAFE_USAGE_PERCENT / LearningConstants.PERCENT_BASE;
    }

    private int kimiContextWindow(String modelKey) {
        if (modelKey.contains("128k")) {
            return LearningConstants.AiContext.KIMI_128K_CONTEXT_WINDOW_TOKENS;
        }
        if (modelKey.contains("32k")) {
            return LearningConstants.AiContext.KIMI_32K_CONTEXT_WINDOW_TOKENS;
        }
        return LearningConstants.AiContext.KIMI_8K_CONTEXT_WINDOW_TOKENS;
    }

    private int supportedWindow(int providerWindow) {
        return Math.min(providerWindow, LearningConstants.AiContext.MAX_SUPPORTED_CONTEXT_TOKENS);
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : "";
    }
}
