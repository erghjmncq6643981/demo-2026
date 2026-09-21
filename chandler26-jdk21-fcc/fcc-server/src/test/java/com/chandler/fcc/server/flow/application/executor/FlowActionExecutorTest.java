package com.chandler.fcc.server.flow.application.executor;

import com.chandler.fcc.common.dto.command.FNodeDialDTO;
import com.chandler.fcc.common.dto.command.FNodeHangupDTO;
import com.chandler.fcc.common.entity.FNodeResult;
import com.chandler.fcc.common.enums.FlowActionExecutorType;
import com.chandler.fcc.common.enums.FlowActionType;
import com.chandler.fcc.common.protocol.FNodeMethod;
import com.chandler.fcc.server.command.FccClient;
import com.chandler.fcc.server.flow.infrastructure.http.ThirdPartyFlowProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 验证三类流程动作执行器的注册、协议类型与失败边界。
 */
class FlowActionExecutorTest {

    /**
     * 注册中心必须拒绝执行器缺失。
     */
    @Test
    void rejectsMissingExecutorType() {
        List<FlowActionExecutor> executors = List.of(
            executor(FlowActionExecutorType.FNODE_COMMAND),
            executor(FlowActionExecutorType.INTERNAL_METHOD)
        );

        assertThrows(
            IllegalStateException.class,
            () -> new FlowActionExecutorRegistry(executors)
        );
    }

    /**
     * 注册中心必须拒绝同一类型的重复实现。
     */
    @Test
    void rejectsDuplicateExecutorType() {
        List<FlowActionExecutor> executors = List.of(
            executor(FlowActionExecutorType.FNODE_COMMAND),
            executor(FlowActionExecutorType.FNODE_COMMAND),
            executor(FlowActionExecutorType.INTERNAL_METHOD),
            executor(FlowActionExecutorType.THIRD_PARTY_API)
        );

        assertThrows(
            IllegalStateException.class,
            () -> new FlowActionExecutorRegistry(executors)
        );
    }

    /**
     * FNode 执行器必须拒绝与方法不匹配的 wire DTO。
     */
    @Test
    void rejectsMismatchedFNodePayload() {
        FNodeFlowActionExecutor executor = new FNodeFlowActionExecutor(mock(FccClient.class));
        FlowActionContext context = FlowActionContext.builder()
            .action(FlowActionType.DIAL_CUSTOMER)
            .commandId("command-a")
            .payload(FNodeHangupDTO.builder().uuid("channel-a").build())
            .build();

        assertThrows(IllegalArgumentException.class, () -> executor.execute(context));
    }

    /**
     * NATS 超时码必须映射为 UNKNOWN，不能当作失败后换标识重拨。
     */
    @Test
    void mapsFNodeTimeoutToUnknown() {
        FccClient client = mock(FccClient.class);
        when(
            client.execute(
                eq(FNodeMethod.DIAL),
                any(FNodeDialDTO.class),
                eq("command-a")
            )
        ).thenReturn(FNodeResult.builder().code(-32000).message("RPC timeout").build());
        FNodeFlowActionExecutor executor = new FNodeFlowActionExecutor(client);
        FlowActionContext context = FlowActionContext.builder()
            .action(FlowActionType.DIAL_CUSTOMER)
            .commandId("command-a")
            .payload(FNodeDialDTO.builder().uuid("channel-a").build())
            .build();

        assertEquals(FlowActionStatus.UNKNOWN, executor.execute(context).getStatus());
    }

    /**
     * 内部动作不得缺少由业务服务显式提供的函数。
     */
    @Test
    void rejectsMissingInternalInvocation() {
        InternalFlowActionExecutor executor = new InternalFlowActionExecutor();
        FlowActionContext context = FlowActionContext.builder()
            .action(FlowActionType.FINALIZE_INBOUND)
            .build();

        assertThrows(IllegalArgumentException.class, () -> executor.execute(context));
    }

    /**
     * 第三方动作只能访问服务端已配置的命名端点。
     */
    @Test
    void rejectsMissingThirdPartyEndpoint() {
        ThirdPartyFlowActionExecutor executor = thirdPartyExecutor(Map.of());
        FlowActionContext context = thirdPartyContext("missing", "command-a");

        assertThrows(IllegalArgumentException.class, () -> executor.execute(context));
    }

    /**
     * 第三方动作必须拒绝非 HTTPS 端点。
     */
    @Test
    void rejectsInsecureThirdPartyEndpoint() {
        ThirdPartyFlowProperties.Endpoint endpoint = new ThirdPartyFlowProperties.Endpoint();
        endpoint.setUrl("http://example.invalid/action");
        endpoint.setTimeout(Duration.ofSeconds(3));
        ThirdPartyFlowActionExecutor executor = thirdPartyExecutor(Map.of("crm", endpoint));

        assertThrows(
            IllegalArgumentException.class,
            () -> executor.execute(thirdPartyContext("crm", "command-a"))
        );
    }

    /**
     * 第三方动作必须携带稳定幂等命令标识。
     */
    @Test
    void rejectsMissingThirdPartyCommandId() {
        ThirdPartyFlowProperties.Endpoint endpoint = new ThirdPartyFlowProperties.Endpoint();
        endpoint.setUrl("https://example.invalid/action");
        endpoint.setTimeout(Duration.ofSeconds(3));
        ThirdPartyFlowActionExecutor executor = thirdPartyExecutor(Map.of("crm", endpoint));

        assertThrows(
            IllegalArgumentException.class,
            () -> executor.execute(thirdPartyContext("crm", ""))
        );
    }

    /**
     * 创建只声明类型的执行器测试替身。
     *
     * @param type 执行器类型
     * @return 测试替身
     */
    private FlowActionExecutor executor(FlowActionExecutorType type) {
        FlowActionExecutor executor = mock(FlowActionExecutor.class);
        when(executor.type()).thenReturn(type);
        return executor;
    }

    /**
     * 使用指定白名单构建第三方执行器。
     *
     * @param endpoints 命名端点白名单
     * @return 执行器
     */
    private ThirdPartyFlowActionExecutor thirdPartyExecutor(
        Map<String, ThirdPartyFlowProperties.Endpoint> endpoints
    ) {
        ThirdPartyFlowProperties properties = new ThirdPartyFlowProperties();
        properties.setEndpoints(endpoints);
        return new ThirdPartyFlowActionExecutor(properties, new ObjectMapper());
    }

    /**
     * 构建第三方动作上下文。
     *
     * @param endpointKey 端点键
     * @param commandId 稳定命令标识
     * @return 动作上下文
     */
    private FlowActionContext thirdPartyContext(String endpointKey, String commandId) {
        return FlowActionContext.builder()
            .action(FlowActionType.INVOKE_CONFIGURED_THIRD_PARTY)
            .endpointKey(endpointKey)
            .commandId(commandId)
            .payload(Map.of("callId", "820000000000000001"))
            .build();
    }
}
