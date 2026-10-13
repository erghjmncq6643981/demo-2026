package com.chandler.fcc.server.call.application;

import static org.junit.jupiter.api.Assertions.*;

import com.chandler.fcc.server.call.infrastructure.persistence.data.CallChannelFact;
import com.chandler.fcc.server.call.infrastructure.persistence.data.CallCleanupCandidate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** 验证单话道、真实终态、快照未知和恢复场景的安全结束边界。 */
class CallEndPolicyTest {
    private final LocalDateTime now = LocalDateTime.of(2026, 10, 3, 17, 0);

    /** 一方结束而另一方仍在评价时，不能结束整个 Call。 */
    @Test
    void keepsCallWhenAgentEndsButCustomerStillExists() {
        assertTrue(CallEndPolicy.decide(call(), List.of(ended("agent")), Set.of("guest"), now).isEmpty());
        assertTrue(CallEndPolicy.decide(call(), List.of(ended("agent")), null, now).isEmpty());
    }

    /** 完整结束事实可在 Sidecar 暂不可用或服务重启后补齐历史通话。 */
    @Test
    void usesAllSourceEndsWithoutSnapshotAndPreservesSourceTime() {
        var decision = CallEndPolicy.decide(call(), List.of(ended("agent"), ended("guest")), null, now).orElseThrow();
        assertEquals("ALL_CHANNELS_ENDED", decision.evidence());
        assertEquals(now.minusHours(2), decision.endedAt());
        assertFalse(decision.channelsAbsent());
    }

    /** 快照未知不能当作空节点；完整空快照才能以未知结果恢复结束。 */
    @Test
    void requiresCompleteSnapshotForMissingEndEvents() {
        assertTrue(CallEndPolicy.decide(call(), List.of(), null, now).isEmpty());
        var recovered = CallEndPolicy.decide(call(), List.of(), Set.of(), now).orElseThrow();
        assertEquals("RECOVERY_UNKNOWN", recovered.cause());
        assertTrue(recovered.channelsAbsent());
    }

    /** 没有任何话道身份时不能仅凭空快照结束。 */
    @Test
    void defersCallWithoutChannelIdentity() {
        var call = call();
        call.setGuestChannelUuid(null);
        call.setAgentChannelUuid(null);
        assertTrue(CallEndPolicy.decide(call, List.of(), Set.of(), now).isEmpty());
    }

    /** 转接残留第三话道仍存在时，即使当前双方已结束也不能兜底关闭。 */
    @Test
    void includesOtherPersistedLegsAndIgnoresDuplicateEndEvents() {
        var other = ended("transfer");
        other.setEndedAt(null);
        assertTrue(CallEndPolicy.decide(call(), List.of(ended("agent"), ended("guest"), other),
            Set.of("transfer"), now).isEmpty());
        assertTrue(CallEndPolicy.decide(call(), List.of(ended("agent"), ended("guest"), ended("guest")),
            null, now).isPresent());
    }

    /** 已有 Call 终态可以幂等收尾，不能重写其真实原因和结束时间。 */
    @Test
    void respectsPersistedTerminalFact() {
        var call = call();
        call.setStatus("ERROR_END");
        call.setResult("USER_BUSY");
        call.setEndedAt(now.minusHours(3));
        var decision = CallEndPolicy.decide(call, List.of(), null, now).orElseThrow();
        assertEquals("USER_BUSY", decision.cause());
        assertEquals(call.getEndedAt(), decision.endedAt());
    }

    /** 创建之前或未来的结束事件不是当前通话有效的结束证据。 */
    @Test
    void rejectsInvalidSourceEndTime() {
        var future = ended("guest");
        future.setEndedAt(now.plusHours(1));
        assertTrue(CallEndPolicy.decide(call(), List.of(ended("agent"), future), null, now).isEmpty());
    }

    /** @return 一通创建于昨日、未结束的双话道通话。 */
    private CallCleanupCandidate call() {
        var call = new CallCleanupCandidate();
        call.setId(9001L);
        call.setVersion(3L);
        call.setStartedAt(now.minusDays(1));
        call.setStatus("CONNECTED");
        call.setAgentChannelUuid("agent");
        call.setGuestChannelUuid("guest");
        return call;
    }

    /** @param uuid 话道标识 @return 源结束事实。 */
    private CallChannelFact ended(String uuid) {
        var fact = new CallChannelFact();
        fact.setCallId(9001L);
        fact.setChannelUuid(uuid);
        fact.setEndedAt(now.minusHours(2));
        fact.setCause("NORMAL_CLEARING");
        return fact;
    }
}
