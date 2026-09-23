package com.chandler.fcc.server.outbound.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chandler.fcc.server.outbound.infrastructure.DialJobMapper;
import com.chandler.fcc.server.telephony.application.AgentIdentityService;
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
            mock(AgentIdentityService.class),
            transactions,
            calls,
            new ObjectMapper()
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
        when(calls.startFor(eq("901001"), eq("13800000000"), anyString()))
            .thenThrow(new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "结果未知"));

        service.dispatch();

        verify(mapper, never()).finishAttempt(any(), any(), any());
        verify(mapper, never()).finishJob(any(), any());
    }

    /**
     * 构造一条可领取的渐进式外呼任务。
     *
     * @return 可由服务补充尝试字段的任务参数
     */
    private Map<String, Object> job() {
        Map<String, Object> row = new HashMap<>();
        row.put("id", "100");
        row.put("owner", "901001");
        row.put("mode", "PROGRESSIVE");
        row.put("maxAttempts", 2);
        row.put("number", "13800000000");
        row.put("attempt", "200");
        return row;
    }
}
