package com.chandler.fcc.admin.business.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

/**
 * fcc-admin 访问 fcc-server 内部客户与自动外呼管理能力的 HTTP 适配器。
 *
 * <p>适配器只转发当前控制台会话令牌，租户和操作者身份仍由 fcc-server 在线核验，
 * 前端不会直接访问运行控制面。</p>
 */
@Component
@RequiredArgsConstructor
public class FccBusinessClient {

    private final ObjectMapper objectMapper;
    private volatile HttpClient httpClient;

    @Value("${fcc.server.base-url:http://127.0.0.1:8085}")
    private String serverBaseUrl;

    /**
     * 调用 fcc-server 内部管理接口。
     *
     * @param method HTTP 方法
     * @param path 内部接口路径
     * @param query 查询参数
     * @param body 可选请求体
     * @return 内部接口的业务数据
     */
    public JsonNode exchange(String method, String path, Map<String, ?> query, Object body) {
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(buildUri(path, query))
                .timeout(Duration.ofSeconds(8))
                .header("satoken", currentToken())
                .header("Content-Type", "application/json");
            if (body == null) {
                request.method(method, HttpRequest.BodyPublishers.noBody());
            } else {
                request.method(method, HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
            }
            HttpResponse<String> response = httpClient().send(
                request.build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            JsonNode root = objectMapper.readTree(response.body());
            if (response.statusCode() < 200 || response.statusCode() >= 300 || root.path("code").asInt() != 200) {
                String message = root.path("message").asText("运行控制服务拒绝了管理请求");
                throw new ResponseStatusException(HttpStatus.valueOf(response.statusCode()), message);
            }
            return root.path("data");
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "运行控制服务暂不可用");
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "运行控制服务暂不可用");
        }
    }

    /**
     * 构造内部接口 URI。
     *
     * @param path 接口路径
     * @param query 查询参数
     * @return 完整 URI
     */
    private URI buildUri(String path, Map<String, ?> query) {
        StringBuilder url = new StringBuilder(serverBaseUrl.replaceAll("/+$", "")).append(path);
        Map<String, ?> values = query == null ? Map.of() : new LinkedHashMap<>(query);
        boolean first = true;
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            if (entry.getValue() == null || entry.getValue().toString().isBlank()) {
                continue;
            }
            url.append(first ? '?' : '&');
            first = false;
            url.append(encode(entry.getKey())).append('=').append(encode(entry.getValue().toString()));
        }
        return URI.create(url.toString());
    }

    /**
     * 读取当前控制台会话令牌。
     *
     * @return 非空 Sa-Token 值
     */
    private String currentToken() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        String token = attributes == null ? null : attributes.getRequest().getHeader("satoken");
        if (token == null || token.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
        }
        return token;
    }

    /**
     * 对查询参数进行 UTF-8 编码。
     *
     * @param value 原始值
     * @return 编码结果
     */
    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /**
     * 在首次真实远程调用时创建 HTTP 客户端，避免应用装配阶段产生网络副作用。
     *
     * @return 可复用的 JDK HTTP 客户端
     */
    private HttpClient httpClient() {
        HttpClient client = httpClient;
        if (client != null) {
            return client;
        }
        synchronized (this) {
            if (httpClient == null) {
                httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(2))
                    .build();
            }
            return httpClient;
        }
    }
}
