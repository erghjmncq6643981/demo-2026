package com.chandler.fcc.server.call;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.chandler.fcc.server.agent.infrastructure.AgentRuntimeMapper;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** 验证单条恢复失败被隔离，数据库整体失败不会伪装为恢复成功。 */
class CallRecoveryServiceTest {
    /** 坏会话与失败审计均不影响后续正常会话注册。 */
    @Test
    void isolatesCorruptRowAndAuditFailure() {
        var mapper = mock(AgentRuntimeMapper.class);
        var sessions = mock(CallSessionManager.class);
        when(sessions.getByCtrlUuid(anyString())).thenReturn(Optional.empty());
        when(mapper.active(0)).thenReturn(List.of(row(1,"NOT_A_STATE"),row(2,"CONNECTED")));
        when(mapper.active(2)).thenReturn(List.of());
        doThrow(new IllegalStateException("unavailable")).when(mapper).recordRecoveryIssue(eq(1L),anyString());
        assertDoesNotThrow(() -> new CallRecoveryService(mapper,sessions).restore());
        verify(mapper).recordRecoveryIssue(eq(1L),anyString());
        verify(sessions).registerSession(argThat(call -> "2".equals(call.getCallId())));
        verify(mapper).resolveRecoveryIssue(2L);
    }

    /** 数据库分页不可用仍显式失败，消费者连接循环可以退避重试。 */
    @Test
    void doesNotHideDatabaseOutage() {
        var mapper = mock(AgentRuntimeMapper.class);
        when(mapper.active(0)).thenThrow(new IllegalStateException("unavailable"));
        assertThrows(IllegalStateException.class,
            () -> new CallRecoveryService(mapper,mock(CallSessionManager.class)).restore());
    }

    /** 修复后的同一会话可重新恢复，不产生新的 Call 或拨号命令。 */
    @Test
    void retriesCorrectedFact() {
        var mapper = mock(AgentRuntimeMapper.class);
        var sessions = mock(CallSessionManager.class);
        when(sessions.getByCtrlUuid(anyString())).thenReturn(Optional.empty());
        when(mapper.active(0)).thenReturn(List.of(row(1,"NOT_A_STATE")),List.of(row(1,"CONNECTED")));
        when(mapper.active(1)).thenReturn(List.of());
        var service = new CallRecoveryService(mapper,sessions);
        service.restore(); service.retryRecovery();
        verify(mapper).recordRecoveryIssue(eq(1L),anyString());
        verify(mapper).resolveRecoveryIssue(1L);
        verify(sessions).registerSession(argThat(call -> "1".equals(call.getCallId())));
    }

    /** @param id 通话标识 @param status 业务阶段 @return 脱敏恢复夹具 */
    private Map<String,Object> row(long id,String status) {
        return Map.of("id",id,"ctrlId","ctrl-"+id,"modelType","INBOUND_CUSTOMER_SERVICE",
            "direction","INBOUND","status",status,"attributes",
            "{\"runtimeTemplate\":\"INBOUND\",\"guestChannelUuid\":\"guest\"}");
    }

    /** 非固定模板的跳过不能被标记为恢复成功。 */
    @Test void skippedTemplateDoesNotCloseIssue() {
        var mapper=mock(AgentRuntimeMapper.class);
        var sessions=mock(CallSessionManager.class);
        when(sessions.getByCtrlUuid(anyString())).thenReturn(Optional.empty());
        var skipped=new HashMap<>(row(1,"CONNECTED"));
        skipped.put("attributes","{}");
        when(mapper.active(0)).thenReturn(List.of(skipped)); when(mapper.active(1)).thenReturn(List.of());
        new CallRecoveryService(mapper,sessions).restore();
        verify(mapper,never()).resolveRecoveryIssue(anyLong());
        verify(sessions,never()).registerSession(any());
    }

    /** 恢复转接目标的精确话道关联，后续事件不依赖最新通话猜测。 */
    @Test void restoresTransferChannelMapping() {
        var mapper=mock(AgentRuntimeMapper.class); var sessions=mock(CallSessionManager.class);
        when(sessions.getByCtrlUuid(anyString())).thenReturn(Optional.empty());
        var transferred=new HashMap<>(row(1,"CONNECTED"));
        transferred.put("attributes","{\"runtimeTemplate\":\"INBOUND\",\"guestChannelUuid\":\"guest\",\"transferChannelUuid\":\"target\"}");
        when(mapper.active(0)).thenReturn(List.of(transferred)); when(mapper.active(1)).thenReturn(List.of());
        new CallRecoveryService(mapper,sessions).restore();
        verify(sessions).bindChannel("target","ctrl-1");
    }
}
