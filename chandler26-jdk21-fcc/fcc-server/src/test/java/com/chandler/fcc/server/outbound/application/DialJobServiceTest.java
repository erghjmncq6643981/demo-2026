package com.chandler.fcc.server.outbound.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chandler.fcc.server.outbound.infrastructure.DialJobMapper;
import com.chandler.fcc.server.flow.FlowConfig;
import com.chandler.fcc.server.telephony.application.OutboundCallService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/**
 * 自动外呼派发未知结果的恢复边界测试。
 */
class DialJobServiceTest {

    private final DialJobMapper mapper = mock(DialJobMapper.class);
    private final TransactionTemplate transactions = mock(TransactionTemplate.class);
    private final OutboundCallService calls = mock(OutboundCallService.class);

    private DialJobService service;

    /**
     * 构造处于全天拨号窗口且无需外部基础设施的调度服务。
     */
    @BeforeEach
    void setUp() {
        service = new DialJobService(
            mapper,
            transactions,
            calls,
            new ObjectMapper(),
            mock(FlowConfig.class)
        );
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "maxInFlight", 5);
        ReflectionTestUtils.setField(service, "startHour", 0);
        ReflectionTestUtils.setField(service, "endHour", 24);
        ReflectionTestUtils.setField(service, "timezone", "Asia/Shanghai");

        when(mapper.expiredDispatches()).thenReturn(List.of());
        when(mapper.unattached()).thenReturn(List.of());
        when(mapper.running()).thenReturn(List.of());
        when(mapper.schedulerLock()).thenReturn(1L);
        when(mapper.activeCount()).thenReturn(0);
        when(mapper.frequency("13800000000")).thenReturn(0);
        when(mapper.countAttempts("100")).thenReturn(0);
        when(mapper.claim("100")).thenReturn(1);
        when(mapper.next()).thenReturn(job());
        when(transactions.execute(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            TransactionCallback<Map<String, Object>> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
    }

    /**
     * 网关超时表示副作用结果未知，尝试必须保持运行等待 Call 事实对账。
     */
    @Test
    void keepsAttemptRunningWhenDispatchOutcomeIsUnknown() {
        when(calls.startAutoDial(
            eq("13800000000"),
            anyString(),
            eq("SYSTEM_NOTIFICATION"),
            anyMap()
        ))
            .thenThrow(new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "结果未知"));

        service.dispatch();

        verify(mapper, never()).finishAttempt(any(), any(), any());
        verify(mapper, never()).finishJob(any(), any());
    }

    /**
     * 无人自动外呼必须直接拨打客户，不能退化为依赖坐席终端的人工外呼。
     */
    @Test
    void dispatchesAutoFlowWithoutAgentDependency() {
        when(calls.startAutoDial(
            eq("13800000000"),
            anyString(),
            eq("SYSTEM_NOTIFICATION"),
            eq(Map.of())
        )).thenReturn(Map.of("callId", "300"));

        service.dispatch();

        verify(calls).startAutoDial(
            eq("13800000000"),
            anyString(),
            eq("SYSTEM_NOTIFICATION"),
            eq(Map.of())
        );
        verify(calls, never()).startFor(anyString(), anyString(), anyString());
        verify(mapper).attach(anyString(), eq("300"));
    }

    /**
     * 漏话回拨是坐席人工外呼，必须保留任务指定的执行坐席。
     */
    @Test
    void dispatchesAgentCallbackForAssignedAgent() {
        when(mapper.next()).thenReturn(callbackJob());
        when(calls.startFor(eq("901001"), eq("13800000000"), anyString()))
            .thenReturn(Map.of("callId", "301"));

        service.dispatch();

        verify(calls).startFor(eq("901001"), eq("13800000000"), anyString());
        verify(calls, never()).startAutoDial(anyString(), anyString(), anyString(), anyMap());
        verify(mapper).attach(anyString(), eq("301"));
    }

    /**
     * 构造一条不绑定坐席的流程型自动外呼任务。
     *
     * @return 可由服务补充尝试字段的任务参数
     */
    private Map<String, Object> job() {
        Map<String, Object> row = new HashMap<>();
        row.put("id", "100");
        row.put("jobType", "AUTO_FLOW");
        row.put("flowKey", "SYSTEM_NOTIFICATION");
        row.put("variables", "{}");
        row.put("maxAttempts", 2);
        row.put("number", "13800000000");
        row.put("attempt", "200");
        return row;
    }

    /**
     * 构造一条需要指定坐席执行的漏话回拨任务。
     *
     * @return 可由服务补充尝试字段的回拨任务参数
     */
    private Map<String, Object> callbackJob() {
        Map<String, Object> row = new HashMap<>();
        row.put("id", "100");
        row.put("jobType", "AGENT_CALLBACK");
        row.put("owner", "901001");
        row.put("maxAttempts", 2);
        row.put("number", "13800000000");
        return row;
    }
}
