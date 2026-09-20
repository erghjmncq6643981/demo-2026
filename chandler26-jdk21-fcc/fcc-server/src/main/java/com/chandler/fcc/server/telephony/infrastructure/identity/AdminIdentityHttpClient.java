package com.chandler.fcc.server.telephony.infrastructure.identity;

import com.chandler.fcc.server.telephony.application.identity.IdentityProfile;
import com.chandler.fcc.server.telephony.application.port.IdentityProfilePort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * 通过 fcc-admin 当前身份接口在线验证登录令牌。
 */
@Component
@RequiredArgsConstructor
public class AdminIdentityHttpClient implements IdentityProfilePort {

    private final ObjectMapper objectMapper;
    private volatile HttpClient httpClient;

    @Value("${fcc.admin.base-url:http://127.0.0.1:8089}")
    private String adminBaseUrl;

    /**
     * 调用统一认证接口并映射可信身份字段。
     *
     * @param token 登录令牌，不得写入日志
     * @return 在线认证后的身份资料
     */
    @Override
    public IdentityProfile authenticate(String token) {
        try {
            HttpRequest request = HttpRequest.newBuilder(
                URI.create(adminBaseUrl.replaceAll("/+$", "") + "/api/admin/auth/me")
            )
                .timeout(Duration.ofSeconds(3))
                .header("satoken", token)
                .GET()
                .build();
            HttpResponse<String> response = httpClient().send(
                request,
                HttpResponse.BodyHandlers.ofString()
            );
            JsonNode root = objectMapper.readTree(response.body());
            if (response.statusCode() != 200 || root.path("code").asInt() != 200) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "登录已失效");
            }
            JsonNode user = root.path("data");
            return new IdentityProfile(
                user.path("loginId").asText(),
                user.path("accountType").asText(),
                permissions(user.path("permissions"))
            );
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "身份验证暂不可用");
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "身份验证暂不可用");
        }
    }

    /**
     * 将权限 JSON 数组映射为字符串列表。
     *
     * @param node 权限数组节点
     * @return 权限码列表
     */
    private List<String> permissions(JsonNode node) {
        List<String> permissions = new ArrayList<>();
        if (node.isArray()) {
            node.forEach(item -> permissions.add(item.asText()));
        }
        return permissions;
    }

    /**
     * 延迟创建可复用的 HTTP 客户端，避免应用装配阶段产生网络资源副作用。
     *
     * @return 带连接超时的 HTTP 客户端
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
