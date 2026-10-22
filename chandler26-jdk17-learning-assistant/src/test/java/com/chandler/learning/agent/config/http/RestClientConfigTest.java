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
    @DisplayName("代理开关关闭时，显式配置 NO_PROXY 直连，防止系统级代理污染")
    void returnsDirectRestTemplateWhenProxyDisabled() {
        RestTemplate restTemplate = config.restTemplate(
                builder, connectTimeout, readTimeout, false, "127.0.0.1", 7897);

        assertThat(restTemplate).isNotNull();
        assertThat(restTemplate.getRequestFactory()).isInstanceOf(SimpleClientHttpRequestFactory.class);
        SimpleClientHttpRequestFactory factory = (SimpleClientHttpRequestFactory) restTemplate.getRequestFactory();
        Proxy proxy = extractProxy(factory);
        assertThat(proxy).isEqualTo(Proxy.NO_PROXY);
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
    @DisplayName("代理开关开启但主机名为空或端口非正数时，安全降级为直连 RestTemplate 并显式配置 NO_PROXY")
    void fallsBackToDirectWhenProxyHostOrPortInvalid() {
        RestTemplate emptyHost = config.restTemplate(
                builder, connectTimeout, readTimeout, true, "", 7897);
        assertThat(emptyHost).isNotNull();
        assertThat(extractProxy((SimpleClientHttpRequestFactory) emptyHost.getRequestFactory())).isEqualTo(Proxy.NO_PROXY);

        RestTemplate zeroPort = config.restTemplate(
                builder, connectTimeout, readTimeout, true, "127.0.0.1", 0);
        assertThat(zeroPort).isNotNull();
        assertThat(extractProxy((SimpleClientHttpRequestFactory) zeroPort.getRequestFactory())).isEqualTo(Proxy.NO_PROXY);
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
