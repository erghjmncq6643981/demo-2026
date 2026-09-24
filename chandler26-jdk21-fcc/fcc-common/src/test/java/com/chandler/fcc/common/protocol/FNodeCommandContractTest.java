package com.chandler.fcc.common.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.chandler.fcc.common.dto.command.FNodePlayDTO;
import com.chandler.fcc.common.dto.command.FNodeAnswerDTO;
import com.chandler.fcc.common.dto.command.FNodeRecordDTO;
import com.chandler.fcc.common.dto.command.FNodeTransferDTO;
import com.chandler.fcc.common.dto.command.MediaInfo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/**
 * Java 与 Sidecar 之间的规范 FNode 命令序列化契约测试。
 */
class FNodeCommandContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 应答与播放驻留使用独立命令，话道应答事实仍来自 Channel 事件。
     *
     * @throws Exception JSON 序列化失败
     */
    @Test
    void serializesBindingAnswerAndParkPlayback() throws Exception {
        JsonNode answer = objectMapper.valueToTree(
            FNodeAnswerDTO.builder().ctrlUuid("ctrl-9001").uuid("channel-9001").build()
        );
        JsonNode play = objectMapper.valueToTree(
            FNodePlayDTO.builder()
                .ctrlUuid("ctrl-9001")
                .uuid("channel-9001")
                .media(MediaInfo.builder().type(FNodeMediaType.TEXT).data("绑定成功").build())
                .actionAfter(FNodePlayPostAction.PARK)
                .build()
        );
        assertEquals("ctrl-9001", answer.path("ctrl_uuid").asText());
        assertEquals("channel-9001", answer.path("uuid").asText());
        assertEquals("PARK", play.path("action_after").asText());
    }

    /**
     * 转接只输出业务目标与 context，不泄漏 FreeSWITCH 表达式字段。
     *
     * @throws Exception JSON 序列化失败
     */
    @Test
    void serializesStructuredTransferTarget() throws Exception {
        JsonNode json = objectMapper.valueToTree(
            FNodeTransferDTO.builder()
                .ctrlUuid("ctrl-test")
                .uuid("00000000-0000-0000-0000-000000000001")
                .target("901001")
                .context("default")
                .build()
        );

        assertEquals("ctrl-test", json.path("ctrl_uuid").asText());
        assertEquals("901001", json.path("target").asText());
        assertEquals("default", json.path("context").asText());
        assertFalse(json.has("destination"));
        assertFalse(json.has("dialplan"));
    }

    /**
     * 媒体、播放后置动作和录音动作使用公共枚举的规范 wire 值。
     *
     * @throws Exception JSON 序列化失败
     */
    @Test
    void serializesCanonicalCommandOptions() throws Exception {
        JsonNode play = objectMapper.valueToTree(
            FNodePlayDTO.builder()
                .ctrlUuid("ctrl-test")
                .uuid("00000000-0000-0000-0000-000000000001")
                .media(MediaInfo.builder().type(FNodeMediaType.TEXT).data("您好").build())
                .actionAfter(FNodePlayPostAction.HANGUP)
                .build()
        );
        JsonNode record = objectMapper.valueToTree(
            FNodeRecordDTO.builder()
                .ctrlUuid("ctrl-test")
                .uuid("00000000-0000-0000-0000-000000000001")
                .action(FNodeRecordAction.STOP)
                .path("/recordings/test.wav")
                .build()
        );

        assertEquals("TEXT", play.path("media").path("type").asText());
        assertEquals("HANGUP", play.path("action_after").asText());
        assertEquals("STOP", record.path("action").asText());
    }
}
