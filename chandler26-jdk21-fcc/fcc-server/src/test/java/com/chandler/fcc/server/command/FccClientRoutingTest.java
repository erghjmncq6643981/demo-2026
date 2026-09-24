package com.chandler.fcc.server.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chandler.fcc.common.dto.command.FNodeDialDTO;
import com.chandler.fcc.common.entity.FNodeResult;
import com.chandler.fcc.server.infrastructure.nats.FccProperties;
import com.chandler.fcc.server.infrastructure.persistence.entity.CallCommandEntity;
import com.chandler.fcc.server.infrastructure.persistence.service.CallPersistenceService;
import io.nats.client.Connection;
import io.nats.client.Message;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

/**
 * 验证业务命令不携带节点参数并统一进入逻辑分发主题。
 */
class FccClientRoutingTest {

    /**
     * Dial 请求必须发送到 fs.cmd.dispatch，并保留 Sidecar 返回的节点事实。
     *
     * @throws Exception NATS 测试替身配置失败
     */
    @Test
    void sendsDialToLogicalDispatchSubject() throws Exception {
        Connection connection = mock(Connection.class);
        Message reply = mock(Message.class);
        when(reply.getData()).thenReturn(
            (
                "{\"jsonrpc\":\"2.0\",\"id\":\"dial-channel-a\",\"result\":{" +
                "\"code\":200,\"message\":\"OK\",\"node_id\":\"node-a\"}}"
            ).getBytes(StandardCharsets.UTF_8)
        );
        when(
            connection.request(
                eq("fs.cmd.dispatch"),
                any(byte[].class),
                any(Duration.class)
            )
        ).thenReturn(reply);
        FccProperties properties = new FccProperties();
        properties.setRpcTimeoutMillis(1000);
        CallPersistenceService persistence = mock(CallPersistenceService.class);
        FccClient client = new FccClient(connection, properties, persistence);
        FNodeDialDTO command = FNodeDialDTO.builder()
            .uuid("channel-a")
            .destination(
                FNodeDialDTO.Destination.builder()
                    .callParams(
                        List.of(
                            FNodeDialDTO.CallParam.builder()
                                .uuid("channel-a")
                                .dialString("1001")
                                .context("default")
                                .build()
                        )
                    )
                    .build()
            )
            .build();

        FNodeResult result = client.dial(command);

        assertEquals("node-a", result.getNodeId());
        ArgumentCaptor<byte[]> payload = ArgumentCaptor.forClass(byte[].class);
        verify(connection).request(
            eq("fs.cmd.dispatch"),
            payload.capture(),
            any(Duration.class)
        );
        String wire = new String(payload.getValue(), StandardCharsets.UTF_8);
        assertFalse(wire.contains("node_id"));
        InOrder order = inOrder(persistence, connection);
        order.verify(persistence).recordCommand(any(CallCommandEntity.class));
        order.verify(connection).request(eq("fs.cmd.dispatch"), any(byte[].class), any(Duration.class));
        order.verify(persistence).recordCommandReceipt(
            eq("dial-channel-a"), eq("node-a"), eq("ACCEPTED"), any(), eq(null), eq(null)
        );
    }

    /**
     * 命令意图持久化失败必须阻止远程拨号，不能只写一条调试日志。
     *
     * @throws Exception Mock NATS 替身配置失败
     */
    @Test
    void neverSendsWhenCommandIntentCannotBePersisted() throws Exception {
        Connection connection = mock(Connection.class);
        CallPersistenceService persistence = mock(CallPersistenceService.class);
        doThrow(new IllegalStateException("database unavailable"))
            .when(persistence).recordCommand(any(CallCommandEntity.class));
        FccClient client = new FccClient(connection, new FccProperties(), persistence);

        assertThrows(
            IllegalStateException.class,
            () -> client.dial(FNodeDialDTO.builder().uuid("channel-b").build())
        );
        verify(connection, never()).request(any(), any(byte[].class), any(Duration.class));
    }
}
