package com.chandler.fcc.admin.flow.application.coordinator;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

/** 验证发布后的通知边界；HTTP 与 Redis 均为模拟适配器，不访问真实运行端。 */
class FlowRuntimeNotifierTest {
    /** 未配置运行端时仅广播，保持惰性 HTTP 客户端初始化。 */
    @Test
    void broadcastsWithoutInitializingUnconfiguredHttp() {
        var redis = mock(StringRedisTemplate.class);
        var notifier = new FlowRuntimeNotifier(redis, new ObjectMapper());
        notifier.notifyRuntime("TEST_FLOW");
        verify(redis).convertAndSend("fcc:flow:publish", "TEST_FLOW");
        assertNull(ReflectionTestUtils.getField(notifier, "httpClient"));
    }

    /** 广播或 HTTP 失败不能抛出异常回滚已经提交的发布事实。 */
    @Test
    void containsBroadcastAndAsyncHttpFailure() {
        var redis = mock(StringRedisTemplate.class);
        doThrow(new IllegalStateException("unavailable")).when(redis).convertAndSend(anyString(), anyString());
        var notifier = new FlowRuntimeNotifier(redis, new ObjectMapper());
        var client = mock(HttpClient.class);
        ReflectionTestUtils.setField(notifier, "httpClient", client);
        ReflectionTestUtils.setField(notifier, "fccServerBaseUrl", "http://fcc.example");
        ReflectionTestUtils.setField(notifier, "reloadToken", "fixture-token-not-a-secret");
        when(client.sendAsync(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
            .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("no responder")));
        assertDoesNotThrow(() -> notifier.notifyRuntime("TEST_FLOW"));
        verify(client).sendAsync(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
    }

    /** HTTP 重载请求保留端点、参数编码、鉴权头和原超时，响应不是业务成功的替代。 */
    @Test
    @SuppressWarnings("unchecked")
    void preservesReloadWireContract() {
        var notifier = new FlowRuntimeNotifier(mock(StringRedisTemplate.class), new ObjectMapper());
        var client = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"data\":{\"reloaded\":true}}");
        when(client.sendAsync(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
            .thenReturn(CompletableFuture.completedFuture(response));
        ReflectionTestUtils.setField(notifier, "httpClient", client);
        ReflectionTestUtils.setField(notifier, "fccServerBaseUrl", "http://fcc.example///");
        ReflectionTestUtils.setField(notifier, "reloadToken", "fixture-token-not-a-secret");
        notifier.notifyRuntime("TEST FLOW");
        var request = ArgumentCaptor.forClass(HttpRequest.class);
        verify(client).sendAsync(request.capture(), ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
        assertEquals("http://fcc.example/api/telephony/call/flow/reload?flowKey=TEST+FLOW", request.getValue().uri().toString());
        assertEquals("POST", request.getValue().method());
        assertEquals(Duration.ofMillis(1500), request.getValue().timeout().orElseThrow());
        assertEquals("fixture-token-not-a-secret", request.getValue().headers().firstValue("X-FCC-Reload-Token").orElseThrow());
    }
}
