package com.chandler.learning.agent.domain.enums;

import com.chandler.learning.agent.exception.LearningAssistantException;
import com.chandler.learning.agent.support.LearningConstants;
import lombok.Getter;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Locale;

/**
 * 当前产品支持的 AI 供应商。
 */
@Getter
public enum AiProvider {

    DEEPSEEK("deepseek", "DeepSeek", "https://api.deepseek.com", "/chat/completions",
            AiApiProtocol.OPENAI_CHAT_COMPLETIONS),
    KIMI("kimi", "Kimi", "https://api.moonshot.cn/v1", "/chat/completions",
            AiApiProtocol.OPENAI_CHAT_COMPLETIONS);

    private final String code;
    private final String title;
    private final String defaultBaseUrl;
    private final String defaultChatPath;
    private final AiApiProtocol apiProtocol;

    AiProvider(String code, String title, String defaultBaseUrl, String defaultChatPath,
               AiApiProtocol apiProtocol) {
        this.code = code;
        this.title = title;
        this.defaultBaseUrl = defaultBaseUrl;
        this.defaultChatPath = defaultChatPath;
        this.apiProtocol = apiProtocol;
    }

    /**
     * 根据稳定供应商编码解析枚举。
     */
    public static AiProvider of(String code) {
        if (!StringUtils.hasText(code)) {
            throw LearningAssistantException.badRequest(LearningConstants.ErrorCode.AI_PROVIDER_MISSING);
        }
        String normalized = code.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(provider -> provider.code.equals(normalized) || provider.name().equalsIgnoreCase(normalized))
                .findFirst()
                .orElseThrow(() -> LearningAssistantException.badRequest(
                        LearningConstants.ErrorCode.AI_PROVIDER_UNSUPPORTED,
                        "当前不支持 AI 供应商：" + code));
    }
}
