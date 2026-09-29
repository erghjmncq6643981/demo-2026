package com.chandler.fcc.server.telephony.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.chandler.fcc.server.agent.infrastructure.AgentRuntimeMapper;
import com.chandler.fcc.server.agent.infrastructure.data.AgentRuntimeStateData;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 呼叫准入与防盗打风控单元测试
 */
class CallAdmissionServiceTest {

    private final AgentRuntimeMapper agents = mock(AgentRuntimeMapper.class);
    private CallAdmissionService admissionService;

    @BeforeEach
    void setUp() {
        admissionService = new CallAdmissionService(agents);
    }

    @Test
    void rejectsEmptyOrNullExtension() {
        var res1 = admissionService.checkAgentAdmission(null);
        assertFalse(res1.admitted());
        assertEquals("UNAUTHENTICATED_EXTENSION", res1.reasonCode());

        var res2 = admissionService.checkAgentAdmission("  ");
        assertFalse(res2.admitted());
        assertEquals("UNAUTHENTICATED_EXTENSION", res2.reasonCode());
    }

    @Test
    void rejectsUnboundExtension() {
        when(agents.agentByEndpoint("1099")).thenReturn(null);
        var res = admissionService.checkAgentAdmission("1099");
        assertFalse(res.admitted());
        assertEquals("AGENT_NOT_FOUND", res.reasonCode());
    }

    @Test
    void rejectsOfflineAgent() {
        when(agents.agentByEndpoint("1001")).thenReturn(Map.of("workNo", "901001"));
        AgentRuntimeStateData presence = new AgentRuntimeStateData();
        presence.setLoginStatus("LOGOUT");
        when(agents.presence("901001")).thenReturn(presence);

        var res = admissionService.checkAgentAdmission("1001");
        assertFalse(res.admitted());
        assertEquals("AGENT_OFFLINE", res.reasonCode());
    }

    @Test
    void admitsOnlineAgent() {
        when(agents.agentByEndpoint("1001")).thenReturn(Map.of("workNo", "901001"));
        AgentRuntimeStateData presence = new AgentRuntimeStateData();
        presence.setLoginStatus("LOGIN");
        when(agents.presence("901001")).thenReturn(presence);

        var res = admissionService.checkAgentAdmission("1001");
        assertTrue(res.admitted());
        assertEquals("901001", res.workNo());
    }

    @Test
    void admitsBusyAgent() {
        when(agents.agentByEndpoint("1001")).thenReturn(Map.of("workNo", "901001"));
        AgentRuntimeStateData presence = new AgentRuntimeStateData();
        presence.setLoginStatus("LOGIN_BUSY");
        when(agents.presence("901001")).thenReturn(presence);

        var res = admissionService.checkAgentAdmission("1001");
        assertTrue(res.admitted());
    }

    @Test
    void rejectsInternationalNumbers() {
        var r1 = admissionService.checkDestinationRisk("0085212345678");
        assertFalse(r1.admitted());
        assertEquals("FORBIDDEN_INTERNATIONAL", r1.reasonCode());

        var r2 = admissionService.checkDestinationRisk("+18001234567");
        assertFalse(r2.admitted());
        assertEquals("FORBIDDEN_INTERNATIONAL", r2.reasonCode());

        var r3 = admissionService.checkDestinationRisk("0118613800000000");
        assertFalse(r3.admitted());
        assertEquals("FORBIDDEN_INTERNATIONAL", r3.reasonCode());
    }

    @Test
    void rejectsForbiddenPremiumPrefixes() {
        var r1 = admissionService.checkDestinationRisk("16888888");
        assertFalse(r1.admitted());
        assertEquals("FORBIDDEN_PREMIUM_PREFIX", r1.reasonCode());

        var r2 = admissionService.checkDestinationRisk("95013800000");
        assertFalse(r2.admitted());
        assertEquals("FORBIDDEN_PREMIUM_PREFIX", r2.reasonCode());

        var r3 = admissionService.checkDestinationRisk("4008888888");
        assertFalse(r3.admitted());
        assertEquals("FORBIDDEN_PREMIUM_PREFIX", r3.reasonCode());
    }

    @Test
    void admitsValidDomesticAndInternalNumbers() {
        var r1 = admissionService.checkDestinationRisk("13800138000");
        assertTrue(r1.admitted());

        var r2 = admissionService.checkDestinationRisk("02158888888");
        assertTrue(r2.admitted());

        var r3 = admissionService.checkDestinationRisk("1008");
        assertTrue(r3.admitted());
    }
}
