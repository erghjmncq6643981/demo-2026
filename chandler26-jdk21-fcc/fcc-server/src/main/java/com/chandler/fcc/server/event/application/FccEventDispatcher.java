package com.chandler.fcc.server.event.application;

import com.chandler.fcc.common.protocol.FccEventMethod;
import com.chandler.fcc.common.protocol.FccEventField;
import com.chandler.fcc.server.call.CallSessionManager;
import com.chandler.fcc.server.event.handler.FccEventHandler;
import com.chandler.fcc.server.infrastructure.persistence.entity.CallEventEntity;
import com.chandler.fcc.server.infrastructure.persistence.service.CallPersistenceService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 解析标准事件信封并将其分发给唯一的类型处理器。
 */
@Service
@RequiredArgsConstructor
public class FccEventDispatcher {

    private static final String EVENT_PROCESSED = "PROCESSED";
    private static final String EVENT_FAILED = "FAILED";
    private static final String EVENT_RECEIVED = "RECEIVED";

    private final ObjectMapper objectMapper;
    private final List<FccEventHandler> handlers;
    private final CallSessionManager sessions;
    private final CallPersistenceService persistence;

    /**
     * 解析并分发原始事件字节。
     *
     * @param payload UTF-8 JSON 事件信封
     * @throws IOException 事件信封不是合法 JSON
     * @throws IllegalArgumentException 方法或参数不符合标准协议
     */
    public void dispatch(byte[] payload) throws IOException {
        JsonNode root = objectMapper.readTree(payload);
        String wireMethod = root.path("method").asText();
        JsonNode params = root.path("params");
        if (wireMethod.isBlank() || params.isMissingNode() || params.isNull()) {
            throw new IllegalArgumentException("事件缺少 method 或 params");
        }
        FccEventMethod method = FccEventMethod.fromWireName(wireMethod);
        List<FccEventHandler> matched = handlers.stream().filter(candidate -> candidate.supports(method)).toList();
        if (matched.size() != 1) {
            throw new IllegalStateException(
                "事件方法必须且只能有一个处理器: method=" + wireMethod + ", handlers=" + matched.size()
            );
        }
        FccEventHandler handler = matched.getFirst();
        String eventId = params.path(FccEventField.EVENT_ID.getWireName()).asText(null);
        CallEventEntity event = event(params, method, payload, eventId);
        persistence.receiveEvent(event);
        Long callIdBeforeHandling = relatedCallId(params);
        try {
            handler.handle(params);
            persistence.finishEvent(
                eventId,
                EVENT_PROCESSED,
                null,
                relatedCallId(params, callIdBeforeHandling)
            );
        } catch (RuntimeException failure) {
            persistence.finishEvent(
                eventId,
                EVENT_FAILED,
                failure.getClass().getSimpleName(),
                relatedCallId(params, callIdBeforeHandling)
            );
            throw failure;
        }
    }

    /**
     * 将事件信封转换为可审计的持久事实。
     *
     * @param params 事件参数
     * @param method 事件方法
     * @param payload 原始 JSON 字节
     * @param eventId 事件标识
     * @return 事件事实
     */
    private CallEventEntity event(
        JsonNode params,
        FccEventMethod method,
        byte[] payload,
        String eventId
    ) {
        long sourceMillis = params.path(FccEventField.SOURCE_TIMESTAMP.getWireName()).asLong(0L);
        LocalDateTime eventTime = sourceMillis > 0
            ? LocalDateTime.ofInstant(Instant.ofEpochMilli(sourceMillis), ZoneOffset.UTC)
            : LocalDateTime.now(ZoneOffset.UTC);
        return CallEventEntity.builder()
            .eventId(eventId)
            .nodeId(params.path(FccEventField.NODE_ID.getWireName()).asText())
            .channelUuid(params.path(FccEventField.CHANNEL_UUID.getWireName()).asText(null))
            .eventType(method.getWireName())
            .eventTime(eventTime)
            .normalizedPayload(params.toString())
            .rawPayload(new String(payload, StandardCharsets.UTF_8))
            .processStatus(EVENT_RECEIVED)
            .build();
    }

    /**
     * 在处理器完成后查找当前事件对应的业务通话。
     *
     * @param params 事件参数
     * @return 数值业务通话标识，可为空
     */
    private Long relatedCallId(JsonNode params) {
        String controlId = params.path(FccEventField.CONTROL_ID.getWireName()).asText(null);
        String channelUuid = params.path(FccEventField.CHANNEL_UUID.getWireName()).asText(null);
        return sessions
            .getByCtrlUuid(controlId)
            .or(() -> sessions.getByChannelUuid(channelUuid))
            .map(call -> Long.valueOf(call.getCallId()))
            .orElse(null);
    }

    /**
     * 优先使用处理前已解析的通话标识，避免终态处理器移除内存会话后丢失关联。
     *
     * @param params 事件参数
     * @param callIdBeforeHandling 处理前的通话标识
     * @return 业务通话标识，可为空
     */
    private Long relatedCallId(JsonNode params, Long callIdBeforeHandling) {
        Long current = relatedCallId(params);
        return current == null ? callIdBeforeHandling : current;
    }
}
