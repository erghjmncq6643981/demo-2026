package com.chandler.fcc.common.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.chandler.fcc.common.dto.flow.ThirdPartyFlowRequest;
import com.chandler.fcc.common.dto.flow.ThirdPartyFlowResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 验证第三方流程请求/响应协议的字段名称和结构边界。
 */
class ThirdPartyFlowContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 请求必须携带版本、幂等命令和 FCC 业务上下文。
     *
     * @throws Exception 序列化失败
     */
    @Test
    void serializesRequiredRequestBoundary() throws Exception {
        ThirdPartyFlowRequest request = ThirdPartyFlowRequest.builder()
            .commandId("command-a")
            .callId("820000000000000001")
            .flowInstanceId("820000000000000021")
            .action("INVOKE_CONFIGURED_THIRD_PARTY")
            .input(Map.of("confirmed", true))
            .build();

        var json = objectMapper.readTree(objectMapper.writeValueAsString(request));

        assertEquals(ThirdPartyFlowProtocol.VERSION, json.path("protocol_version").asText());
        assertEquals("command-a", json.path("command_id").asText());
        assertEquals("820000000000000001", json.path("call_id").asText());
        assertTrue(json.path("input").path("confirmed").asBoolean());
    }

    /**
     * 响应必须能还原结构化 data，供流程阶段记录和分支判断使用。
     *
     * @throws Exception 反序列化失败
     */
    @Test
    void deserializesStructuredResponse() throws Exception {
        String source = """
            {"protocol_version":"1.0","command_id":"command-a","accepted":true,
             "code":"OK","message":"accepted","data":{"next":"CALLBACK"},"retryable":false}
            """;

        ThirdPartyFlowResponse response = objectMapper.readValue(source, ThirdPartyFlowResponse.class);

        assertTrue(response.getAccepted());
        assertEquals("command-a", response.getCommandId());
        assertEquals("CALLBACK", response.getData().path("next").asText());
    }
}
