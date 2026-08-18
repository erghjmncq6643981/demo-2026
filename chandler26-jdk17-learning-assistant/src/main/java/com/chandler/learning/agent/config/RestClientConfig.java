package com.chandler.learning.agent.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * HTTP 客户端配置。
 */
@Configuration
public class RestClientConfig {

    /**
     * 处理 {@code restTemplate} 相关业务。
     */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder,
                                     @org.springframework.beans.factory.annotation.Value("${learning.ai.http.connect-timeout:10s}") Duration connectTimeout,
                                     @org.springframework.beans.factory.annotation.Value("${learning.ai.http.read-timeout:90s}") Duration readTimeout) {
        return builder
                .setConnectTimeout(connectTimeout)
                .setReadTimeout(readTimeout)
                .build();
    }
}
