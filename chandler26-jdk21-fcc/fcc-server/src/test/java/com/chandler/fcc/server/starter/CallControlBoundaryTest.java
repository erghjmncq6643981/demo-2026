package com.chandler.fcc.server.starter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.chandler.fcc.common.dto.command.FNodeTransferDTO;
import com.chandler.fcc.common.entity.CallInfoBO;
import com.chandler.fcc.common.entity.FNodeResult;
import com.chandler.fcc.common.enums.FlowActionType;
import com.chandler.fcc.server.call.CallSessionManager;
import com.chandler.fcc.server.command.FccClient;
import com.chandler.fcc.server.flow.application.FlowActionExecutionService;
import com.chandler.fcc.server.telephony.application.AgentIdentityService;
import com.chandler.fcc.server.telephony.application.CallControlService;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

/**
 * 话务控制授权、失败语义与规范 FNode 动作边界测试。
 */
class CallControlBoundaryTest {

    private final CallSessionManager sessions = mock(CallSessionManager.class);
    private final FccClient client = mock(FccClient.class);
    private final AgentIdentityService identity = mock(AgentIdentityService.class);
    private final FlowActionExecutionService actions = mock(FlowActionExecutionService.class);
    private final CallControlService service = new CallControlService(
        sessions,
        client,
        actions,
        identity
    );

    /**
     * 缺少 Call 或跨坐席时不能回落到最近会话。
     */
    @Test
    void rejectsMissingOrForeignCall() {
        when(identity.requirePrincipal()).thenReturn(
            new AgentIdentityService.Principal("alice")
        );
        assertThrows(
            ResponseStatusException.class,
            () -> service.requireCall("alice", null)
        );
        verifyNoInteractions(sessions, client);

        when(sessions.getByCallId("call-test")).thenReturn(
            Optional.of(CallInfoBO.builder().agentWorkNo("bob").build())
        );
        assertThrows(
            ResponseStatusException.class,
            () -> service.requireCall("alice", "call-test")
        );
        verifyNoInteractions(client);
        verify(sessions, never()).getLatestActiveSession();
    }

    /**
     * 超时、节点业务错误以及封装在成功 RPC 中的 ESL 错误均非成功。
     */
    @Test
    void rejectsNegativeAcknowledgements() {
        FNodeResult[] responses = {
            null,
            FNodeResult.builder().code(-32000).build(),
            FNodeResult.builder().code(500).build(),
            FNodeResult.builder()
                .code(200)
                .data(Map.of("response", "-ERR failed"))
                .build()
        };
        for (FNodeResult response : responses) {
            assertThrows(
                ResponseStatusException.class,
                () -> CallControlService.requireAccepted(response)
            );
        }
    }

    /**
     * 已受理的转接只发送业务目标与 context，且不提前结束原会话。
     */
    @Test
    void transferAcknowledgementDoesNotEndCall() {
        CallInfoBO call = CallInfoBO.builder()
            .callId("call-test")
            .nodeId("node-test")
            .ctrlId("ctrl-test")
            .guestChannelUuid("00000000-0000-0000-0000-000000000001")
            .build();
        when(actions.executeFNode(any(), any(), any(), any())).thenReturn(null);

        Map<?, ?> data = (Map<?, ?>) service.transfer(call, "1001").get("data");

        assertEquals("ACCEPTED", data.get("status"));
        ArgumentCaptor<FNodeTransferDTO> command = ArgumentCaptor.forClass(
            FNodeTransferDTO.class
        );
        verify(actions).executeFNode(
            eq(call),
            eq(FlowActionType.TRANSFER_CALL),
            command.capture(),
            eq("transfer-call-test")
        );
        assertEquals("1001", command.getValue().getTarget());
        assertEquals("default", command.getValue().getContext());
        verifyNoInteractions(sessions);
        verify(client, never()).hangup(anyString(), anyString(), anyString());
    }
}
