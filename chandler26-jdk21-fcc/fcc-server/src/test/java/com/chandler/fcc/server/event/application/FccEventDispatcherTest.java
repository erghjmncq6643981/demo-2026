package com.chandler.fcc.server.event.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.chandler.fcc.common.protocol.FccEventMethod;
import com.chandler.fcc.server.call.CallSessionManager;
import com.chandler.fcc.server.event.handler.FccEventHandler;
import com.chandler.fcc.server.infrastructure.persistence.entity.CallEventEntity;
import com.chandler.fcc.server.infrastructure.persistence.service.CallPersistenceService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 验证事件分发先记录原始事实，再回填处理状态。
 */
class FccEventDispatcherTest {

    private static final String EVENT_ID = "b".repeat(64);

    /**
     * 处理器成功后事件必须从 RECEIVED 变为 PROCESSED。
     *
     * @throws Exception JSON 解析失败
     */
    @Test
    void persistsEventBeforeAndAfterDispatch() throws Exception {
        FccEventHandler handler = mock(FccEventHandler.class);
        CallPersistenceService persistence = mock(CallPersistenceService.class);
        FccEventDispatcher dispatcher = new FccEventDispatcher(
            new ObjectMapper(),
            List.of(handler),
            mock(CallSessionManager.class),
            persistence
        );
        String payload = "{\"method\":\"Event.Channel\",\"params\":{" +
            "\"event_id\":\"" + EVENT_ID + "\",\"node_id\":\"node-a\"," +
            "\"timestamp\":1700000000000,\"uuid\":\"channel-a\"}}";

        org.mockito.Mockito.when(handler.supports(FccEventMethod.CHANNEL)).thenReturn(true);
        dispatcher.dispatch(payload.getBytes(StandardCharsets.UTF_8));

        verify(persistence).receiveEvent(any(CallEventEntity.class));
        verify(persistence).finishEvent(eq(EVENT_ID), eq("PROCESSED"), eq(null), eq(null));
        verify(handler).handle(any(JsonNode.class));
    }
}
