package com.chandler.fcc.server.call.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.chandler.fcc.common.entity.FNodeResult;
import com.chandler.fcc.server.command.FccClient;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** 验证超时、无响应、不完整和重连成功的快照证据边界。 */
class ChannelSnapshotReaderTest {
    /** 失败均为未知，重连后的完整空集合才表示节点没有话道。 */
    @Test
    void distinguishesFailureFromCompleteEmptySnapshot() {
        var client = mock(FccClient.class);
        var reader = new ChannelSnapshotReader(client);
        when(client.channelSnapshot()).thenThrow(new IllegalStateException("RPC_TIMEOUT"))
            .thenThrow(new IllegalStateException("NO_RESPONDERS"))
            .thenReturn(FNodeResult.builder().code(200).data(Map.of("complete", false, "channel_uuids", List.of())).build())
            .thenReturn(FNodeResult.builder().code(200).data(Map.of("complete", true, "channel_uuids", List.of())).build());
        assertNull(reader.read());
        assertNull(reader.read());
        assertNull(reader.read());
        assertEquals(Set.of(), reader.read());
    }

    /** 重复或非法 UUID 的快照不能声明完整。 */
    @Test
    void rejectsInvalidAndDuplicateChannelRows() {
        var client = mock(FccClient.class);
        var reader = new ChannelSnapshotReader(client);
        when(client.channelSnapshot()).thenReturn(FNodeResult.builder().code(200)
            .data(Map.of("complete", true, "channel_uuids", List.of("same", "same"))).build());
        assertNull(reader.read());
    }
}
