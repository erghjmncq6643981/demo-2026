package com.chandler.fcc.server.starter;

import com.chandler.fcc.common.entity.CallInfoBO;
import com.chandler.fcc.common.entity.FNodeResult;
import com.chandler.fcc.server.call.CallSessionManager;
import com.chandler.fcc.server.command.FccClient;
import com.chandler.fcc.server.telephony.application.AgentIdentityService;
import com.chandler.fcc.server.telephony.application.CallControlService;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 核对目标隔离、失败语义与命令受理不会伪造结束事实。 */
class CallControlBoundaryTest {
    private final CallSessionManager sessions = mock(CallSessionManager.class);
    private final FccClient client = mock(FccClient.class);
    private final AgentIdentityService identity = mock(AgentIdentityService.class);
    private final CallControlService service = new CallControlService(sessions, client, identity);

    /** 缺少 Call 或跨坐席时不能回落到最近会话。 */
    @Test void rejectsMissingOrForeignCall() {
        when(identity.requirePrincipal()).thenReturn(new AgentIdentityService.Principal("alice"));
        assertThrows(ResponseStatusException.class, () -> service.requireCall("alice", null));
        verifyNoInteractions(sessions, client);
        when(sessions.getByCallId("call-test")).thenReturn(Optional.of(CallInfoBO.builder().agentWorkNo("bob").build()));
        assertThrows(ResponseStatusException.class, () -> service.requireCall("alice", "call-test"));
        verifyNoInteractions(client);
        verify(sessions, never()).getLatestActiveSession();
    }

    /** 超时、节点业务错误以及封装在成功 RPC 中的 ESL 错误均非成功。 */
    @Test void rejectsNegativeAcknowledgements() {
        for (FNodeResult response : new FNodeResult[]{null, FNodeResult.builder().code(-32000).build(),
                FNodeResult.builder().code(500).build(), FNodeResult.builder().code(200).data(Map.of("response", "-ERR failed")).build()}) {
            assertThrows(ResponseStatusException.class, () -> CallControlService.requireAccepted(response));
        }
    }

    /** 已受理的转接不删除原会话，也不提前挂断原坐席。 */
    @Test void transferAcknowledgementDoesNotEndCall() {
        var call = CallInfoBO.builder().callId("call-test").nodeId("node-test").ctrlId("ctrl-test")
                .guestChannelUuid("00000000-0000-0000-0000-000000000001").build();
        when(client.nativeAPI(eq("node-test"), eq("uuid_transfer"), anyString()))
                .thenReturn(FNodeResult.builder().code(200).data(Map.of("response", "+OK")).build());
        assertEquals("ACCEPTED", ((Map<?, ?>) service.transfer(call, "1001").get("data")).get("status"));
        verifyNoInteractions(sessions);
        verify(client, never()).hangup(anyString(), anyString(), anyString(), anyString());
    }
}
