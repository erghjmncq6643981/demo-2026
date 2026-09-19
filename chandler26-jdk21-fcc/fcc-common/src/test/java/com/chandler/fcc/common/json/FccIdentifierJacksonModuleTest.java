package com.chandler.fcc.common.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FCC 标识符序列化契约测试。
 */
class FccIdentifierJacksonModuleTest {

    /**
     * 验证 Long 标识符输出字符串且普通 Long 度量仍输出数字。
     *
     * @throws Exception JSON 序列化或解析失败时抛出
     */
    @Test
    void serializesOnlyLongIdentifiersAsStrings() throws Exception {
        JsonMapper mapper = JsonMapper.builder()
                .addModule(FccIdentifierJacksonModule.create())
                .build();

        JsonNode json = mapper.readTree(mapper.writeValueAsBytes(
                new Fixture(9007199254740993L, 9007199254740995L, 45000L)));

        assertTrue(json.path("id").isTextual());
        assertEquals("9007199254740993", json.path("id").asText());
        assertTrue(json.path("callId").isTextual());
        assertEquals("9007199254740995", json.path("callId").asText());
        assertTrue(json.path("durationMs").isNumber());
        assertEquals(45000L, json.path("durationMs").asLong());
    }

    private record Fixture(Long id, Long callId, Long durationMs) {
    }
}
