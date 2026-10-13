package com.chandler.fcc.server.call.application;

import com.chandler.fcc.server.agent.infrastructure.AgentParticipationMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.chandler.fcc.server.agent.application.AgentLegLifecycleService;
import com.chandler.fcc.server.call.domain.CallEndDecision;
import com.chandler.fcc.server.call.infrastructure.persistence.data.CallCleanupCandidate;
import com.chandler.fcc.server.call.infrastructure.persistence.mapper.CallEndMapper;
import com.chandler.fcc.server.outbound.application.DialJobReconciliationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;

/** 验证实时终态收尾、乐观冲突以及恢复重试不产生重复副作用。 */
class CallFinalizationServiceTest {
    private final CallEndMapper mapper = mock(CallEndMapper.class);
    private final DialJobReconciliationService jobs = mock(DialJobReconciliationService.class);
    private final AgentLegLifecycleService agentLegs = mock(AgentLegLifecycleService.class);
    private final CallFinalizationService service = new CallFinalizationService(mapper, jobs, agentLegs,
        mock(AgentParticipationMapper.class));

    /**
     * 源事件先锁 Call 再更新 Leg 和释放，避免并发末尾话道结束产生写偏差。
     * @throws Exception 测试事件 JSON 无法解析
     */
    @Test
    void serializesSourceLegEndingsBeforeRelease() throws Exception {
        when(mapper.lockCalls(anyList())).thenReturn(List.of(call(1L, null)));
        service.recordChannelEnd("9001", new ObjectMapper().readTree("{\"uuid\":\"leg-a\",\"node_id\":\"node-test\"}"));
        var order = inOrder(mapper, agentLegs);
        order.verify(mapper).lockCalls(List.of(9001L));
        order.verify(mapper).recordLegEnd(eq(9001L), eq("leg-a"), eq("node-test"), any(), anyString());
        order.verify(agentLegs).releaseEndedLeg(9001L, "leg-a");
    }

    /** 活跃通话不能由关联收尾入口提前结束。 */
    @Test
    void doesNotFinalizeActiveCall() {
        when(mapper.lockCalls(anyList())).thenReturn(List.of(call(1L, null)));
        service.cleanupEndedCall(9001L);
        verify(mapper, never()).closeSteps(anyList());
        verifyNoInteractions(jobs);
    }

    /** 扫描后版本变化的通话留待下次，不释放可能已经变化的坐席。 */
    @Test
    void defersOptimisticConflictAndCanRetryWithNewVersion() {
        when(mapper.lockCalls(anyList())).thenReturn(List.of(call(2L, null)));
        assertTrue(service.repair(List.of(decision(1L))).isEmpty());
        verify(mapper, never()).completeCalls(anyList());
        when(mapper.completeCalls(anyList())).thenReturn(1);
        assertEquals(List.of(9001L), service.repair(List.of(decision(2L))));
        verify(jobs).reconcileEndedCalls(List.of(9001L));
    }

    /** 行数异常必须抛出以回滚，不能继续写关联表。 */
    @Test
    void abortsWhenCompareAndSetFails() {
        when(mapper.lockCalls(anyList())).thenReturn(List.of(call(1L, null)));
        assertThrows(OptimisticLockingFailureException.class, () -> service.repair(List.of(decision(1L))));
        verify(mapper, never()).releaseAgents(anyList());
    }

    /** 重复实时终态收尾只执行条件更新，不重放 Dial 或录音副作用。 */
    @Test
    void cleansExistingTerminalWithoutRewritingCall() {
        when(mapper.lockCalls(anyList())).thenReturn(List.of(call(1L, LocalDateTime.now())));
        service.cleanupEndedCall(9001L);
        service.cleanupEndedCall(9001L);
        verify(mapper, never()).completeCalls(anyList());
        verify(mapper, times(2)).markRecordingsUnknown(List.of(9001L));
        verify(mapper, times(2)).closeLegs(List.of(9001L), false);
    }

    /** @param version 当前版本 @param end 结束时间 @return 持久通话摘要。 */
    private CallCleanupCandidate call(Long version, LocalDateTime end) {
        var row = new CallCleanupCandidate();
        row.setId(9001L);
        row.setVersion(version);
        row.setStatus(end == null ? "CONNECTED" : "NORMAL_END");
        row.setEndedAt(end);
        return row;
    }

    /** @param version 检查时版本 @return 完整快照缺失结束决策。 */
    private CallEndDecision decision(Long version) {
        return new CallEndDecision(9001L, version, LocalDateTime.now(), "RECOVERY_UNKNOWN",
            "COMPLETE_SNAPSHOT_ABSENT", true);
    }
}
