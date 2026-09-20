package com.chandler.fcc.server.flow.application.executor;

import com.chandler.fcc.common.enums.FlowActionExecutorType;
import com.chandler.fcc.server.flow.infrastructure.http.ThirdPartyFlowProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * 仅调用服务端预配置命名端点的第三方 HTTP 执行器。
 */
@Component
public class ThirdPartyFlowActionExecutor implements FlowActionExecutor {

    private final ThirdPartyFlowProperties properties;
    private final ObjectMapper objectMapper;
    private volatile HttpClient httpClient;

    /**
     * 创建第三方接口执行器，HTTP 客户端在首次真实调用时才初始化。
     *
     * @param properties 服务端端点白名单
     * @param objectMapper JSON 序列化器
     */
    public ThirdPartyFlowActionExecutor(
        ThirdPartyFlowProperties properties,
        ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * 返回第三方接口执行器类型。
     *
     * @return 第三方接口类型
     */
    @Override
    public FlowActionExecutorType type() {
        return FlowActionExecutorType.THIRD_PARTY_API;
    }

    /**
     * 调用预配置端点；模型和请求上下文均不能提供 URL。
     *
     * @param context 动作上下文
     * @return HTTP 请求即时受理结果
     */
    @Override
    public FlowActionResult execute(FlowActionContext context) {
        if (context.getEndpointKey() == null || context.getEndpointKey().isBlank()) {
            throw new IllegalArgumentException("第三方端点键不能为空");
        }
        if (context.getCommandId() == null || context.getCommandId().isBlank()) {
            throw new IllegalArgumentException("第三方动作缺少稳定命令标识");
        }
        ThirdPartyFlowProperties.Endpoint endpoint = properties
            .getEndpoints()
            .get(context.getEndpointKey());
        if (endpoint == null || endpoint.getUrl() == null || endpoint.getUrl().isBlank()) {
            throw new IllegalArgumentException("第三方端点未配置: " + context.getEndpointKey());
        }
        URI uri = URI.create(endpoint.getUrl());
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("第三方流程端点必须使用 HTTPS");
        }
        Duration timeout = endpoint.getTimeout();
        if (
            timeout == null ||
            timeout.isZero() ||
            timeout.isNegative() ||
            timeout.compareTo(Duration.ofSeconds(30)) > 0
        ) {
            throw new IllegalArgumentException("第三方流程端点超时必须在 1 毫秒至 30 秒之间");
        }
        try {
            HttpClient client = httpClient();
            HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", context.getCommandId())
                .POST(
                    HttpRequest.BodyPublishers.ofByteArray(
                        objectMapper.writeValueAsBytes(context.getPayload())
                    )
                )
                .build();
            HttpResponse<String> response = client.send(
                request,
                HttpResponse.BodyHandlers.ofString()
            );
            boolean accepted = response.statusCode() >= 200 && response.statusCode() < 300;
            return FlowActionResult.builder()
                .status(accepted ? FlowActionStatus.ACCEPTED : FlowActionStatus.FAILED)
                .commandId(context.getCommandId())
                .code(Integer.toString(response.statusCode()))
                .message(accepted ? "第三方接口已受理" : "第三方接口拒绝请求")
                .build();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return unknown(context, "第三方接口调用被中断");
        } catch (Exception failure) {
            return unknown(context, "第三方接口结果未知");
        }
    }

    /**
     * 构造不可安全重试的未知结果。
     *
     * @param context 动作上下文
     * @param message 可读说明
     * @return 未知结果
     */
    private FlowActionResult unknown(FlowActionContext context, String message) {
        return FlowActionResult.builder()
            .status(FlowActionStatus.UNKNOWN)
            .commandId(context.getCommandId())
            .message(message)
            .build();
    }

    /**
     * 惰性创建并复用 JDK HTTP 客户端，避免未启用第三方动作时影响服务启动。
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
                httpClient = HttpClient.newBuilder().build();
            }
            return httpClient;
        }
    }
}
