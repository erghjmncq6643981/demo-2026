package com.chandler.fcc.server.telephony.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

/**
 * 通过管理服务验证坐席令牌，控制面不信任调用方声明的工号。
 */
@Service
public class AgentIdentityService {

    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${fcc.admin.base-url:http://127.0.0.1:8089}")
    private String adminBaseUrl;

    /**
     * 验证当前 HTTP 请求的坐席身份及显式工号。
     *
     * @param requestedWorkNo 请求工号，可为空但不可冒用
     * @return 认证坐席工号
     */
    public String requireAgent(String requestedWorkNo) {
        var attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        String token = attributes == null ? null : attributes.getRequest().getHeader("satoken");
        String workNo = authenticate(token);
        if (requestedWorkNo != null && !requestedWorkNo.isBlank() && !workNo.equals(requestedWorkNo)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "坐席身份不匹配");
        }
        return workNo;
    }

    /**
     * 对令牌进行在线认证，不缓存过期或已注销的身份。
     *
     * @param token 登录令牌，不得记录到日志
     * @return 认证坐席工号
     */
    public String authenticate(String token) {
        return authenticateProfile(token).path("loginId").asText();
    }

    /**
     * 已认证业务主体，仅由身份服务产生。
     *
     * @param workNo 坐席工号或管理账号
     */
    public record Principal(String workNo) {}

    /**
     * 验证当前请求并取得业务主体。
     *
     * @return 当前请求的可信业务主体
     */
    public Principal requirePrincipal() {
        var attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return authenticatePrincipal(
            attributes == null ? null : attributes.getRequest().getHeader("satoken")
        );
    }

    /**
     * 在线验证长连接身份。
     *
     * @param token 登录令牌，不得记录
     * @return 可信坐席主体
     */
    public Principal authenticatePrincipal(String token) {
        JsonNode user = authenticateProfile(token);
        return new Principal(user.path("loginId").asText());
    }

    /**
     * 在线读取身份资料。
     *
     * @param token 不得记录的登录令牌
     * @return 已验证的身份资料
     */
    private JsonNode authenticateProfile(String token) {
        return authenticateProfile(token, "AGENT");
    }

    /**
     * 管理端通过在线身份核验进入运行业务管理入口。
     *
     * @return 已认证管理主体
     */
    public Principal requireManagement() {
        var attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        var user = authenticateProfile(
            attributes == null ? null : attributes.getRequest().getHeader("satoken"),
            "CONSOLE"
        );
        if (
            !"ADMIN".equals(user.path("role").asText()) &&
            !"OPERATOR".equals(user.path("role").asText()) &&
            !"SUPER_ADMIN".equals(user.path("role").asText())
        ) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅系统管理员可以管理客户和外呼任务");
        return new Principal(user.path("loginId").asText());
    }

    /**
     * 按账户类型在线验证，不允许坐席令牌冒用管理入口。
     *
     * @param token 登录令牌
     * @param accountType 必须匹配的账户类型
     * @return 身份资料
     */
    private JsonNode authenticateProfile(String token, String accountType) {
        if (token == null || token.isBlank()) throw new ResponseStatusException(
            HttpStatus.UNAUTHORIZED,
            "请先登录"
        );
        try {
            var request = HttpRequest.newBuilder(
                URI.create(adminBaseUrl.replaceAll("/+$", "") + "/api/admin/auth/me")
            )
                .timeout(Duration.ofSeconds(3))
                .header("satoken", token)
                .GET()
                .build();
            var response = createHttpClient().send(
                request,
                HttpResponse.BodyHandlers.ofString()
            );
            JsonNode root = mapper.readTree(response.body());
            JsonNode user = root.path("data");
            if (
                response.statusCode() != 200 ||
                root.path("code").asInt() != 200 ||
                !accountType.equals(user.path("accountType").asText()) ||
                user.path("loginId").asText().isBlank()
            ) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "坐席登录已失效");
            }
            return user;
        } catch (ResponseStatusException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "身份验证暂不可用");
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "身份验证暂不可用");
        }
    }

    /**
     * 在真正发起认证时创建 HTTP 客户端，避免应用装配阶段产生网络资源副作用。
     *
     * @return 带连接超时的 JDK HTTP 客户端
     */
    private HttpClient createHttpClient() {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    }
}
