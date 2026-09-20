package com.chandler.fcc.server.starter;

import com.chandler.fcc.server.call.LegStatePolicy;
import com.chandler.fcc.server.infrastructure.persistence.service.CallPersistenceService;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 验证重复、乱序与未知事件不会回退话道事实。 */
class LegStatePolicyTest {
    /** 非法标识必须拒绝，不能通过截前缀或哈希关联到另一通话。 */
    @Test void rejectsNonCanonicalCallIds() {
        assertEquals(123L, CallPersistenceService.parseNumericId("123"));
        for (String invalid : new String[]{"call-123", "", "0", "-1", "0123", "abc", "9999999999999999999"}) {
            assertThrows(IllegalArgumentException.class, () -> CallPersistenceService.parseNumericId(invalid));
        }
        assertThrows(IllegalArgumentException.class, () -> CallPersistenceService.parseNumericId(null));
    }
    /** 覆盖接听后迟到振铃、终态幂等与未知协议值。 */
    @Test void preservesMonotonicLifecycle() {
        assertTrue(LegStatePolicy.accepts("START", "READY"));
        assertTrue(LegStatePolicy.accepts("READY", "BRIDGE"));
        assertFalse(LegStatePolicy.accepts("READY", "RINGING"));
        assertFalse(LegStatePolicy.accepts("BRIDGE", "READY"));
        assertFalse(LegStatePolicy.accepts("DESTROY", "BRIDGE"));
        assertTrue(LegStatePolicy.accepts("DESTROY", "DESTROY"));
        assertFalse(LegStatePolicy.accepts("BRIDGE", "UNRECOGNIZED"));
        assertTrue(LegStatePolicy.accepts(null, "DESTROY"));
    }
}
