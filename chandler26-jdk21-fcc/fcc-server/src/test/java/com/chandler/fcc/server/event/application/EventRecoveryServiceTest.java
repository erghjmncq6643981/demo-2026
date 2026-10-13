package com.chandler.fcc.server.event.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.chandler.fcc.common.entity.CallInfoBO;
import com.chandler.fcc.server.call.application.CallFinalizationService;
import com.chandler.fcc.server.event.infrastructure.EventInboxMapper;
import com.chandler.fcc.server.telephony.application.AgentIdentityService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

/** 验证受控修复不重放媒体副作用，精确关联、租约与持久审计为前置条件。 */
class EventRecoveryServiceTest {
    private final String eventId = "a".repeat(64);
    private final EventInboxMapper inbox = mock(EventInboxMapper.class);
    private final CallFinalizationService finalization = mock(CallFinalizationService.class);
    private final AgentIdentityService identity = mock(AgentIdentityService.class);
    private final EventRecoveryService service = new EventRecoveryService(inbox,finalization,identity,new ObjectMapper());

    /** @param state 源状态 @param id 源事件标识 @return 已验证来源的脱敏信封夹具 */
    private String payload(String state,String id) {
        return "{\"method\":\"Event.Channel\",\"params\":{\"event_id\":\""+id+
            "\",\"state\":\""+state+"\",\"uuid\":\"leg\",\"node_id\":\"node\",\"timestamp\":1700000000000}}";
    }

    /** 初始化可领取的异常记录；不初始化任何媒体客户端。 */
    private void claimable() {
        when(identity.requireRuntimeManagement()).thenReturn(new AgentIdentityService.Principal("operator"));
        when(inbox.claimRecovery(eq(eventId),anyString(),eq("operator"))).thenReturn(1);
        when(inbox.recordRecoveryAudit(eq(eventId),anyString(),eq("operator"))).thenReturn(1);
        when(inbox.finishRecovery(eq(eventId),anyString(),anyString(),nullable(String.class))).thenReturn(1);
    }

    /** 只修复精确关联的源 DESTROY 事实，完成持久审计。 */
    @Test void repairsKnownTerminalFact() {
        claimable(); when(inbox.recoveryPayload(eventId)).thenReturn(payload("DESTROY",eventId));
        when(finalization.resolveCall(null,"leg","node")).thenReturn(CallInfoBO.builder().callId("123").build());
        service.reconcile(eventId);
        var order=inOrder(inbox,finalization);
        order.verify(inbox).recordRecoveryAudit(eq(eventId),anyString(),eq("operator"));
        order.verify(finalization).resolveCall(null,"leg","node");
        order.verify(finalization).recordChannelEnd(eq("123"),any());
        order.verify(finalization).cleanupEndedCall(123L);
        verify(inbox).finishRecoveryAudit(anyString(),eq("RECONCILED"),isNull());
    }

    /** 非终态事件不进入通话处理，保持未知供人工调查。 */
    @Test void doesNotReplayStart() {
        claimable(); when(inbox.recoveryPayload(eventId)).thenReturn(payload("START",eventId));
        assertThrows(ResponseStatusException.class,()->service.reconcile(eventId));
        verifyNoInteractions(finalization);
        verify(inbox).finishRecovery(eq(eventId),anyString(),eq("UNKNOWN"),anyString());
    }

    /** 没有精确话道关联时不猜测最新通话。 */
    @Test void unknownChannelRemainsUnknown() {
        claimable(); when(inbox.recoveryPayload(eventId)).thenReturn(payload("DESTROY",eventId));
        assertThrows(ResponseStatusException.class,()->service.reconcile(eventId));
        verify(finalization,never()).recordChannelEnd(anyString(),any());
    }

    /** 重复请求或其他实例已持有租约时，不读取载荷或产生副作用。 */
    @Test void rejectsConcurrentClaim() {
        when(identity.requireRuntimeManagement()).thenReturn(new AgentIdentityService.Principal("operator"));
        assertThrows(ResponseStatusException.class,()->service.reconcile(eventId));
        verify(inbox,never()).recoveryPayload(anyString()); verifyNoInteractions(finalization);
    }

    /** 信封身份不匹配或审计无法落盘均禁止事实修改。 */
    @Test void rejectsInvalidIdentityAndMissingAudit() {
        claimable(); when(inbox.recoveryPayload(eventId)).thenReturn(payload("DESTROY","b".repeat(64)));
        assertThrows(ResponseStatusException.class,()->service.reconcile(eventId));
        when(inbox.recordRecoveryAudit(eq(eventId),anyString(),eq("operator"))).thenReturn(0);
        assertThrows(ResponseStatusException.class,()->service.reconcile(eventId));
        verifyNoInteractions(finalization);
    }
}
