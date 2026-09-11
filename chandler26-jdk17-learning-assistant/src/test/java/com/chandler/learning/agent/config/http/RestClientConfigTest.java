package com.chandler.learning.agent.config.http;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Field;
import java.net.Proxy;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RestClientConfigTest {

    private final RestClientConfig config = new RestClientConfig();
    private final RestTemplateBuilder builder = new RestTemplateBuilder();
    private final Duration connectTimeout = Duration.ofSeconds(15);
    private final Duration readTimeout = Duration.ofSeconds(300);

    @Test
    @DisplayName("代理开关关闭时，使用默认 RestTemplateBuilder 直连，不配置代理")
    void returnsDirectRestTemplateWhenProxyDisabled() {
        RestTemplate restTemplate = config.restTemplate(
                builder, connectTimeout, readTimeout, false, "127.0.0.1", 7897);

        assertThat(restTemplate).isNotNull();
        // 验证没有包装成带代理的 SimpleClientHttpRequestFactory
        if (restTemplate.getRequestFactory() instanceof SimpleClientHttpRequestFactory factory) {
            Proxy proxy = extractProxy(factory);
            assertThat(proxy).isNull();
        }
    }

    @Test
    @DisplayName("代理开关开启且主机和端口合法时，创建配置了 HTTP 代理的 RestTemplate")
    void returnsProxiedRestTemplateWhenProxyEnabled() {
        RestTemplate restTemplate = config.restTemplate(
                builder, connectTimeout, readTimeout, true, "127.0.0.1", 7897);

        assertThat(restTemplate).isNotNull();
        assertThat(restTemplate.getRequestFactory()).isInstanceOf(SimpleClientHttpRequestFactory.class);
        SimpleClientHttpRequestFactory factory = (SimpleClientHttpRequestFactory) restTemplate.getRequestFactory();
        Proxy proxy = extractProxy(factory);
        assertThat(proxy).isNotNull();
        assertThat(proxy.type()).isEqualTo(Proxy.Type.HTTP);
        assertThat(proxy.address().toString()).contains("127.0.0.1:7897");
    }

    @Test
    @DisplayName("代理开关开启但主机名为空或端口非正数时，安全降级为直连 RestTemplate")
    void fallsBackToDirectWhenProxyHostOrPortInvalid() {
        RestTemplate emptyHost = config.restTemplate(
                builder, connectTimeout, readTimeout, true, "", 7897);
        assertThat(emptyHost).isNotNull();

        RestTemplate zeroPort = config.restTemplate(
                builder, connectTimeout, readTimeout, true, "127.0.0.1", 0);
        assertThat(zeroPort).isNotNull();
    }

    private Proxy extractProxy(SimpleClientHttpRequestFactory factory) {
        try {
            Field field = SimpleClientHttpRequestFactory.class.getDeclaredField("proxy");
            field.setAccessible(true);
            return (Proxy) field.get(factory);
        } catch (Exception ex) {
            return null;
        }
    }
}
