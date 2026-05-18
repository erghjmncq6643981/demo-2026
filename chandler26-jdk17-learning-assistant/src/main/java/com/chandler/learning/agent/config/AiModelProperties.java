package com.chandler.learning.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * AI 模型供应商配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "learning.ai")
public class AiModelProperties {

    private String defaultProvider = "deepseek";

    private Map<String, ProviderConfig> providers = new HashMap<>();

    public ProviderConfig getProvider(String provider) {
        return providers.get(provider);
    }

    @Data
    public static class ProviderConfig {
        private Boolean enabled = true;
        private String apiKey;
        private String baseUrl;
        private String chatPath = "/chat/completions";
        private String defaultModel;
        private Integer connectTimeoutMillis = 10000;
        private Integer readTimeoutMillis = 120000;
    }
}
