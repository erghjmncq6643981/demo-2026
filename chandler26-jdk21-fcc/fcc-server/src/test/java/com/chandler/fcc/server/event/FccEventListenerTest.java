package com.chandler.fcc.server.event;

import com.chandler.fcc.common.protocol.FccEventMethod;
import com.chandler.fcc.common.protocol.NatsSubjectFactory;
import com.chandler.fcc.server.call.CallRecoveryService;
import com.chandler.fcc.server.event.application.FccEventDispatcher;
import com.chandler.fcc.server.event.domain.EventInboxStatus;
import com.chandler.fcc.server.event.infrastructure.EventInboxMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.Connection;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证持久事件入口的来源校验、幂等和有界重试行为。
 */
class FccEventListenerTest {

    private static final String EVENT_ID = "a".repeat(64);
    private static final String NODE_ID = "node-a";
    private static final String SUBJECT = NatsSubjectFactory.event(
        NODE_ID,
        FccEventMethod.CHANNEL
    );
    private static final byte[] PAYLOAD = (
        "{\"method\":\"Event.Channel\",\"params\":{" +
        "\"event_id\":\"" +
        EVENT_ID +
        "\",\"node_id\":\"" +
        NODE_ID +
        "\"}}"
    ).getBytes(StandardCharsets.UTF_8);

    /**
     * 明确的业务处理失败必须抛出，让 JetStream 延迟重投同一事件。
     *
     * @throws Exception 测试中的模拟业务失败
     */
    @Test
    void retriesExplicitProcessingFailure() throws Exception {
        EventInboxMapper inbox = mock(EventInboxMapper.class);
        FccEventDispatcher dispatcher = mock(FccEventDispatcher.class);
        FccEventListener listener = listener(inbox, dispatcher);
        when(inbox.receive(eq(EVENT_ID), eq(NODE_ID), any(String.class))).thenReturn(1, 0);
        when(inbox.status(EVENT_ID)).thenReturn(EventInboxStatus.FAILED.getDatabaseValue());
        when(inbox.claimRetry(EVENT_ID, 5)).thenReturn(1);
        when(
            inbox.finish(
                EVENT_ID,
                EventInboxStatus.PROCESSED.getDatabaseValue(),
                null
            )
        ).thenReturn(1);
        doThrow(new IllegalStateException("temporary"))
            .doNothing()
            .when(dispatcher)
            .dispatch(PAYLOAD);

        assertThrows(
            IllegalStateException.class,
            () -> listener.processDurable(SUBJECT, PAYLOAD)
        );
        assertTrue(listener.processDurable(SUBJECT, PAYLOAD));
        verify(inbox).finish(
            EVENT_ID,
            EventInboxStatus.FAILED.getDatabaseValue(),
            IllegalStateException.class.getSimpleName()
        );
        verify(inbox).claimRetry(EVENT_ID, 5);
    }

    /**
     * 已完成的重复事件必须直接确认且不能重复分发。
     *
     * @throws Exception JSON 解析或数据库访问异常
     */
    @Test
    void skipsProcessedDuplicate() throws Exception {
        EventInboxMapper inbox = mock(EventInboxMapper.class);
        FccEventDispatcher dispatcher = mock(FccEventDispatcher.class);
        FccEventListener listener = listener(inbox, dispatcher);
        when(inbox.receive(eq(EVENT_ID), eq(NODE_ID), any(String.class))).thenReturn(0);
        when(inbox.status(EVENT_ID)).thenReturn(EventInboxStatus.PROCESSED.getDatabaseValue());

        assertTrue(listener.processDurable(SUBJECT, PAYLOAD));
        verify(dispatcher, never()).dispatch(any(byte[].class));
    }

    /**
     * 上次处理中断时副作用未知，不自动重放并转为对账状态。
     *
     * @throws Exception JSON 解析或数据库访问异常
     */
    @Test
    void marksInterruptedProcessingUnknown() throws Exception {
        EventInboxMapper inbox = mock(EventInboxMapper.class);
        FccEventDispatcher dispatcher = mock(FccEventDispatcher.class);
        FccEventListener listener = listener(inbox, dispatcher);
        when(inbox.receive(eq(EVENT_ID), eq(NODE_ID), any(String.class))).thenReturn(0);
        when(inbox.status(EVENT_ID)).thenReturn(EventInboxStatus.PROCESSING.getDatabaseValue());

        assertTrue(listener.processDurable(SUBJECT, PAYLOAD));
        verify(inbox).finish(
            EVENT_ID,
            EventInboxStatus.UNKNOWN.getDatabaseValue(),
            "PREVIOUS_PROCESS_INTERRUPTED"
        );
        verify(dispatcher, never()).dispatch(any(byte[].class));
    }

    /**
     * 事件主题与信封节点不一致时必须终止，且不能进入 Inbox。
     *
     * @throws Exception JSON 解析异常
     */
    @Test
    void rejectsMismatchedSubject() throws Exception {
        EventInboxMapper inbox = mock(EventInboxMapper.class);
        FccEventDispatcher dispatcher = mock(FccEventDispatcher.class);
        FccEventListener listener = listener(inbox, dispatcher);

        assertFalse(listener.processDurable("fs.event.other.channel", PAYLOAD));
        verify(inbox, never()).receive(any(String.class), any(String.class), any(String.class));
        verify(dispatcher, never()).dispatch(any(byte[].class));
    }

    /**
     * 构建不启动消费者线程的监听器测试对象。
     *
     * @param inbox Inbox Mapper
     * @param dispatcher 事件分发器
     * @return 监听器测试对象
     */
    private FccEventListener listener(
        EventInboxMapper inbox,
        FccEventDispatcher dispatcher
    ) {
        return new FccEventListener(
            mock(Connection.class),
            new ObjectMapper(),
            inbox,
            mock(CallRecoveryService.class),
            dispatcher
        );
    }
}
