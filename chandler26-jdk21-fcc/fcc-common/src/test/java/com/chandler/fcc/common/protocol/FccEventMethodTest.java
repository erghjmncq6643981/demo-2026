package com.chandler.fcc.common.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Sidecar 录音事件契约测试。
 */
class FccEventMethodTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 验证当前 Sidecar 标准录音事件报文能够被控制面识别。
     *
     * @throws Exception JSON 解析失败时抛出
     */
    @Test
    void acceptsCanonicalSidecarRecordingFixture() throws Exception {
        JsonNode fixture = objectMapper.readTree("""
                {
                  "jsonrpc": "2.0",
                  "method": "Event.Recording",
                  "params": {
                    "node_id": "fs-node-01",
                    "ctrl_uuid": "ctrl-01",
                    "uuid": "channel-01",
                    "action": "STOP",
                    "file_path": "/recordings/call-01.wav",
                    "seconds": 45,
                    "timestamp": 1789693905000
                  }
                }
                """);

        assertEquals(
            FccEventMethod.RECORDING,
            FccEventMethod.fromWireName(fixture.path("method").asText())
        );
        assertEquals("/recordings/call-01.wav", fixture.path("params").path("file_path").asText());
        assertEquals(45, fixture.path("params").path("seconds").asInt());
    }
}
