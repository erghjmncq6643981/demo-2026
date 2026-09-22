package com.chandler.fcc.server.call;

import com.chandler.fcc.common.entity.CallInfoBO;
import com.chandler.fcc.server.command.FccClient;
import com.chandler.fcc.server.telephony.application.InboundCallService;
import com.chandler.fcc.server.telephony.application.OutboundCallService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 对账已持久化会话与逻辑聚合话道快照。
 *
 * <p>查询失败或快照不完整时不会推断挂机；只有双方话道在连续完整快照中持续缺失，
 * 才会将会话交给固定模板的恢复终态处理。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CallSnapshotReconciler {

    private static final Set<String> RECOVERABLE_TEMPLATES = Set.of(
        "INBOUND",
        "AGENT_FIRST",
        "AGENT_ORIGINATED",
        "NOTIFICATION"
    );

    private final CallSessionManager sessions;
    private final FccClient client;
    private final InboundCallService inbound;
    private final OutboundCallService outbound;
    private final ChannelAbsenceTracker absence = new ChannelAbsenceTracker();
    private final ObjectMapper json = new ObjectMapper();

    /**
     * 每轮查询一次逻辑聚合快照；连续两分钟双方不存在才以恢复未知结果结束通话。
     */
    @Scheduled(fixedDelay = 15000)
    public void reconcile() {
        Set<String> activeIds = new HashSet<>();
        Set<String> channels = loadSnapshot();
        for (CallInfoBO call : sessions.snapshot()) {
            activeIds.add(call.getCallId());
            if (!isRecoverable(call) || isTerminal(call)) {
                continue;
            }
            if (!absence.confirmed(
                call.getCallId(),
                call.getAgentChannelUuid(),
                call.getGuestChannelUuid(),
                channels,
                System.nanoTime() / 1_000_000
            )) {
                continue;
            }
            recoverMissingCall(call);
        }
        absence.retain(activeIds);
    }

    /**
     * 判断通话是否属于固定运行模板。
     *
     * @param call 当前业务通话
     * @return 属于可恢复模板时返回 {@code true}
     */
    private boolean isRecoverable(CallInfoBO call) {
        return RECOVERABLE_TEMPLATES.contains(call.getDataStr("runtimeTemplate", ""));
    }

    /**
     * 判断通话是否已经进入终态。
     *
     * @param call 当前业务通话
     * @return 已记录终态时返回 {@code true}
     */
    private boolean isTerminal(CallInfoBO call) {
        synchronized (call) {
            return call.getData().containsKey("terminal");
        }
    }

    /**
     * 将持续缺失的通话交给对应业务模板处理未知恢复终态。
     *
     * @param call 当前业务通话
     */
    private void recoverMissingCall(CallInfoBO call) {
        synchronized (call) {
            if (call.getData().containsKey("terminal")) {
                return;
            }
            var event = json.createObjectNode()
                .put("state", "DESTROY")
                .put("uuid", call.getGuestChannelUuid())
                .put("cause", "RECOVERY_UNKNOWN");
            try {
                call.putData("recoveryEvidence", "COMPLETE_SNAPSHOTS_ABSENT_120_SECONDS");
                if (!outbound.event(call, event)) {
                    inbound.event(call, event);
                }
                log.warn(
                    "[通话恢复] 话道持续不存在，结束未知会话 callId={} nodeId={}",
                    call.getCallId(),
                    call.getNodeId()
                );
            } catch (RuntimeException failure) {
                log.error("[通话恢复] 结束事实写入失败 callId={}", call.getCallId(), failure);
            }
        }
    }

    /**
     * 只接受成功、完整、类型正确的聚合快照；错误不等同空节点。
     *
     * @return 完整 UUID 集合；查询失败返回 {@code null}
     */
    private Set<String> loadSnapshot() {
        try {
            var result = client.channelSnapshot();
            if (
                result == null ||
                !result.isSuccess() ||
                !(result.getData() instanceof Map<?, ?> data) ||
                !Boolean.TRUE.equals(data.get("complete")) ||
                !(data.get("channel_uuids") instanceof List<?> rows)
            ) {
                return null;
            }
            Set<String> channels = new HashSet<>();
            for (Object row : rows) {
                if (!(row instanceof String uuid) || uuid.isBlank() || !channels.add(uuid)) {
                    return null;
                }
            }
            return channels;
        } catch (RuntimeException failure) {
            log.debug("[通话恢复] 聚合话道快照查询失败: {}", failure.getMessage());
            return null;
        }
    }
}
