package com.chandler.learning.agent.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.net.InetSocketAddress;
import java.net.Proxy;
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
                                     @Value("${learning.ai.http.connect-timeout:15s}") Duration connectTimeout,
                                     @Value("${learning.ai.http.read-timeout:300s}") Duration readTimeout,
                                     @Value("${learning.ai.http.proxy.host:}") String proxyHost,
                                     @Value("${learning.ai.http.proxy.port:0}") int proxyPort) {
        if (StringUtils.hasText(proxyHost) && proxyPort > 0) {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout((int) connectTimeout.toMillis());
            factory.setReadTimeout((int) readTimeout.toMillis());
            factory.setProxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort)));
            return new RestTemplate(factory);
        }
        return builder
                .setConnectTimeout(connectTimeout)
                .setReadTimeout(readTimeout)
                .build();
    }
}
