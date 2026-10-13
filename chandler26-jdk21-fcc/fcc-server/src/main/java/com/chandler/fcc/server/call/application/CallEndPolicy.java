package com.chandler.fcc.server.call.application;

import com.chandler.fcc.server.call.domain.CallEndDecision;
import com.chandler.fcc.server.call.infrastructure.persistence.data.CallChannelFact;
import com.chandler.fcc.server.call.infrastructure.persistence.data.CallCleanupCandidate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** 根据通话事实和完整话道快照决定是否允许兜底结束；过期本身不是结束证据。 */
public final class CallEndPolicy {
    /** 禁止实例化无状态决策工具。 */
    private CallEndPolicy() {}

    /**
     * 校验所有已知话道的结束证据，不将单 Leg 结束当作整个 Call 结束。
     * @param call 过期通话摘要
     * @param facts 本通话的全部持久话道身份与结束事实
     * @param snapshot 完整当前话道集合，查询失败时为空引用
     * @param now UTC 对账时间
     * @return 可安全提交的决策；话道仍存在或证据不足时为空
     */
    public static Optional<CallEndDecision> decide(CallCleanupCandidate call,
        List<CallChannelFact> facts, Set<String> snapshot, LocalDateTime now) {
        Set<String> channels = new HashSet<>();
        add(channels, call.getGuestChannelUuid());
        add(channels, call.getAgentChannelUuid());
        Map<String, CallChannelFact> ended = new HashMap<>();
        for (CallChannelFact fact : facts) {
            add(channels, fact.getChannelUuid());
            if (fact.getEndedAt() != null && fact.getChannelUuid() != null) {
                ended.merge(fact.getChannelUuid(), fact, (left, right) ->
                    right.getEndedAt().isAfter(left.getEndedAt()) ? right : left);
            }
        }
        if (snapshot != null && channels.stream().anyMatch(snapshot::contains)) return Optional.empty();
        if (call.getEndedAt() != null && Set.of("NORMAL_END", "ERROR_END").contains(call.getStatus())) {
            return Optional.of(new CallEndDecision(call.getId(), call.getVersion(), call.getEndedAt(),
                cause(call.getResult()), "PERSISTED_CALL_END", snapshot != null && !channels.isEmpty()));
        }
        if (channels.isEmpty()) return Optional.empty();
        if (ended.keySet().containsAll(channels)) {
            CallChannelFact last = ended.values().stream()
                .max((a, b) -> a.getEndedAt().compareTo(b.getEndedAt())).orElseThrow();
            if (last.getEndedAt().isBefore(call.getStartedAt()) || last.getEndedAt().isAfter(now)) {
                return Optional.empty();
            }
            return Optional.of(new CallEndDecision(call.getId(), call.getVersion(), last.getEndedAt(),
                cause(last.getCause()), "ALL_CHANNELS_ENDED", snapshot != null));
        }
        return snapshot == null ? Optional.empty() : Optional.of(new CallEndDecision(
            call.getId(), call.getVersion(), now, "RECOVERY_UNKNOWN", "COMPLETE_SNAPSHOT_ABSENT", true));
    }

    /**
     * 收集非空话道身份。
     * @param channels 身份集合
     * @param value 可选话道标识
     */
    private static void add(Set<String> channels, String value) {
        if (value != null && !value.isBlank()) channels.add(value);
    }

    /**
     * 保留原始原因，缺失时明确标为未知。
     * @param value 源原因
     * @return 非空原因码
     */
    private static String cause(String value) {
        return value == null || value.isBlank() || "null".equals(value) ? "RECOVERY_UNKNOWN" : value;
    }
}
