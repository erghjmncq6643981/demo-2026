package com.chandler.fcc.admin.flow.application.coordinator;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** 已提交流程的广播与异步重载通知；失败不回滚发布事实。 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FlowRuntimeNotifier {
    private static final String FLOW_PUBLISH_CHANNEL = "fcc:flow:publish";
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private volatile HttpClient httpClient;

    @Value("${fcc.server.base-url:}")
    private String fccServerBaseUrl;

    @Value("${fcc.flow.reload-token:}")
    private String reloadToken;

    /**
     * 发布提交后广播变更并异步请求运行端重载。
     *
     * @param flowKey 稳定流程代码
     */
    public void notifyRuntime(String flowKey) {
        try {
            redisTemplate.convertAndSend(FLOW_PUBLISH_CHANNEL, flowKey);
        } catch (Exception failure) {
            log.warn("[流程发布] Redis 广播未确认 flowKey={}", flowKey);
        }
        if (fccServerBaseUrl == null || fccServerBaseUrl.isBlank() || reloadToken.isBlank()) {
            log.warn("[流程发布] 未配置运行端重载地址 flowKey={}", flowKey);
            return;
        }
        try {
            String baseUrl = fccServerBaseUrl.replaceAll("/+$", "");
            String encoded = URLEncoder.encode(flowKey, StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/telephony/call/flow/reload?flowKey=" + encoded))
                .header("X-FCC-Reload-Token", reloadToken)
                .timeout(Duration.ofMillis(1500))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
            httpClient().sendAsync(request, HttpResponse.BodyHandlers.ofString()).whenComplete((response, failure) -> {
                if (failure != null || response.statusCode() != 200) {
                    log.warn("[流程发布] 运行端重载未确认 flowKey={}", flowKey);
                    return;
                }
                try {
                    boolean reloaded = objectMapper.readTree(response.body())
                        .path("data")
                        .path("reloaded")
                        .asBoolean(false);
                    if (reloaded) {
                        log.info("[流程发布] 运行端已确认重载 flowKey={}", flowKey);
                    } else {
                        log.warn("[流程发布] 运行端未确认激活 flowKey={}", flowKey);
                    }
                } catch (Exception invalidResponse) {
                    log.warn("[流程发布] 运行端响应无法解析 flowKey={}", flowKey);
                }
            });
        } catch (Exception failure) {
            log.warn("[流程发布] 运行端通知未发出 flowKey={}", flowKey);
        }
    }

    /**
     * 在首次发布通知时才创建 HTTP 客户端，避免未使用运行端通知时影响服务启动。
     *
     * @return 可复用的 HTTP 客户端
     */
    private HttpClient httpClient() {
        HttpClient current = httpClient;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (httpClient == null) {
                httpClient = HttpClient.newHttpClient();
            }
            return httpClient;
        }
    }
}
