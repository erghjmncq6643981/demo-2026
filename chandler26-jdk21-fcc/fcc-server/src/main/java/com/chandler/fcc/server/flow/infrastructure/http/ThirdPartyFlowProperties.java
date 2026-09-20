package com.chandler.fcc.server.flow.infrastructure.http;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 流程第三方接口白名单配置。
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "fcc.flow.third-party")
public class ThirdPartyFlowProperties {

    private Map<String, Endpoint> endpoints = new HashMap<>();

    /**
     * 单个命名第三方端点配置。
     */
    @Getter
    @Setter
    public static class Endpoint {

        private String url;
        private Duration timeout = Duration.ofSeconds(3);
    }
}
