package com.chandler.fcc.server.call.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.chandler.fcc.server.call.infrastructure.AfterCallMapper;
import com.chandler.fcc.server.telephony.application.AgentIdentityService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.TransactionStatus;
import org.springframework.web.server.ResponseStatusException;

/** 小结只写原通话事实，不依赖或修改坐席占用状态。 */
class AfterCallServiceTest {
    private final AfterCallMapper mapper = mock(AfterCallMapper.class);
    private final AgentIdentityService identity = mock(AgentIdentityService.class);
    private final TransactionTemplate transactions = mock(TransactionTemplate.class);

    /** 验证身份、小结原子保存与重复提交的原通话绑定。 */
    @Test
    void savesOnlyTheOriginalAuthorizedCallFact() {
        when(identity.requirePrincipal()).thenReturn(new AgentIdentityService.Principal("agent-test"));
        doAnswer(invocation -> {
            invocation.<Consumer<TransactionStatus>>getArgument(0).accept(null);
            return null;
        }).when(transactions).executeWithoutResult(any());
        when(mapper.lockEnded("agent-test", "9001")).thenReturn("9001");
        var service = new AfterCallService(mapper, identity, transactions, new ObjectMapper());
        service.save("9001", "咨询", "HIGH", "原通话小结");
        service.save("9001", "咨询", "HIGH", "重复提交");
        verify(mapper, times(2)).lockEnded("agent-test", "9001");
        verify(mapper, times(2)).save(eq("agent-test"), eq("9001"), anyString());
        verifyNoMoreInteractions(mapper);
    }

    /** 无权访问或通话尚未结束时不得保存。 */
    @Test
    void rejectsUnavailableEndedCall() {
        when(identity.requirePrincipal()).thenReturn(new AgentIdentityService.Principal("agent-test"));
        doAnswer(invocation -> {
            invocation.<Consumer<TransactionStatus>>getArgument(0).accept(null);
            return null;
        }).when(transactions).executeWithoutResult(any());
        var service = new AfterCallService(mapper, identity, transactions, new ObjectMapper());
        assertThrows(ResponseStatusException.class, () -> service.save("9001", "咨询", "HIGH", ""));
        verify(mapper, never()).save(anyString(), anyString(), anyString());
    }
}
