package com.chandler.fcc.server.event.application;

import com.chandler.fcc.common.protocol.FccEventMethod;
import com.chandler.fcc.server.event.handler.FccEventHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 解析标准事件信封并将其分发给唯一的类型处理器。
 */
@Service
@RequiredArgsConstructor
public class FccEventDispatcher {

    private final ObjectMapper objectMapper;
    private final List<FccEventHandler> handlers;

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
        handler.handle(params);
    }
}
