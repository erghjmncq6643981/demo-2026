package com.chandler.fcc.server.flow.application.executor;

import com.chandler.fcc.common.dto.flow.ThirdPartyFlowRequest;
import com.chandler.fcc.common.dto.flow.ThirdPartyFlowResponse;
import com.chandler.fcc.common.enums.FlowActionExecutorType;
import com.chandler.fcc.common.protocol.ThirdPartyFlowProtocol;
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
        if (context.getCallId() == null || context.getCallId().isBlank()) {
            throw new IllegalArgumentException("第三方动作缺少 FCC 业务通话标识");
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
            ThirdPartyFlowRequest requestPayload = ThirdPartyFlowRequest.builder()
                .commandId(context.getCommandId())
                .callId(context.getCallId())
                .flowInstanceId(context.getFlowInstanceId())
                .action(context.getAction().name())
                .input(context.getPayload())
                .build();
            HttpClient client = httpClient();
            HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", context.getCommandId())
                .POST(
                    HttpRequest.BodyPublishers.ofByteArray(
                        objectMapper.writeValueAsBytes(requestPayload)
                    )
                )
                .build();
            HttpResponse<String> response = client.send(
                request,
                HttpResponse.BodyHandlers.ofString()
            );
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return FlowActionResult.builder()
                    .status(FlowActionStatus.FAILED)
                    .commandId(context.getCommandId())
                    .code(Integer.toString(response.statusCode()))
                    .message("第三方接口拒绝请求")
                    .build();
            }
            ThirdPartyFlowResponse responsePayload = objectMapper.readValue(
                response.body(),
                ThirdPartyFlowResponse.class
            );
            validateResponse(context, responsePayload);
            boolean accepted = Boolean.TRUE.equals(responsePayload.getAccepted());
            return FlowActionResult.builder()
                .status(accepted ? FlowActionStatus.ACCEPTED : FlowActionStatus.FAILED)
                .commandId(context.getCommandId())
                .code(responsePayload.getCode())
                .message(responsePayload.getMessage())
                .output(responsePayload.getData())
                .build();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return unknown(context, "第三方接口调用被中断");
        } catch (Exception failure) {
            return unknown(context, "第三方接口结果未知");
        }
    }

    /**
     * 校验第三方响应是否属于本次协议调用。
     *
     * @param context 当前动作上下文
     * @param response 第三方协议响应
     * @throws IllegalArgumentException 响应不符合 FCC 第三方协议
     */
    private void validateResponse(FlowActionContext context, ThirdPartyFlowResponse response) {
        if (response == null) {
            throw new IllegalArgumentException("第三方响应不能为空");
        }
        if (!ThirdPartyFlowProtocol.VERSION.equals(response.getProtocolVersion())) {
            throw new IllegalArgumentException("第三方响应协议版本不支持");
        }
        if (!context.getCommandId().equals(response.getCommandId())) {
            throw new IllegalArgumentException("第三方响应 commandId 与请求不一致");
        }
        if (response.getAccepted() == null) {
            throw new IllegalArgumentException("第三方响应缺少 accepted");
        }
        if (response.getData() != null && !response.getData().isContainerNode()) {
            throw new IllegalArgumentException("第三方响应 data 必须是 JSON 对象或数组");
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
