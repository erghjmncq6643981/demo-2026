package com.chandler.fcc.server.call;

import com.chandler.fcc.server.command.FccClient;
import com.chandler.fcc.server.telephony.application.InboundCallService;
import com.chandler.fcc.server.telephony.application.OutboundCallService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.List;

/** 对账已持久化会话与节点完整话道快照；不重拨、不伪造真实挂机原因。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CallSnapshotReconciler {
    private final CallSessionManager sessions;
    private final FccClient client;
    private final InboundCallService inbound;
    private final OutboundCallService outbound;
    private final ChannelAbsenceTracker absence = new ChannelAbsenceTracker();
    private final ObjectMapper json = new ObjectMapper();

    /** 每节点一次查询；连续两分钟双方不存在才以恢复未知结果结束并释放占用。 */
    @Scheduled(fixedDelay = 15000)
    public void reconcile() {
        var active = sessions.snapshot();
        Map<String,Set<String>> snapshots = new HashMap<>();
        Set<String> activeIds = new HashSet<>();
        for (var call : active) {
            activeIds.add(call.getCallId());
            if (call.getNodeId() == null || !Set.of("INBOUND", "AGENT_FIRST", "NOTIFICATION")
                    .contains(call.getDataStr("runtimeTemplate", ""))) continue;
            if (!snapshots.containsKey(call.getNodeId())) snapshots.put(call.getNodeId(), snapshot(call.getNodeId()));
            synchronized (call) {
                if (call.getData().containsKey("terminal")) continue;
                if (!absence.confirmed(call.getCallId(),call.getAgentChannelUuid(),call.getGuestChannelUuid(),
                        snapshots.get(call.getNodeId()),System.nanoTime()/1_000_000)) continue;
                var event=json.createObjectNode().put("state","DESTROY").put("uuid",call.getGuestChannelUuid())
                        .put("cause","RECOVERY_UNKNOWN");
                try {
                    call.putData("recoveryEvidence","COMPLETE_SNAPSHOTS_ABSENT_120_SECONDS");
                    if (!outbound.event(call,event)) inbound.event(call,event);
                    log.warn("[通话恢复] 话道持续不存在，结束未知会话 callId={} nodeId={}",call.getCallId(),call.getNodeId());
                } catch (RuntimeException failure) {
                    log.error("[通话恢复] 结束事实写入失败 callId={}",call.getCallId());
                }
            }
        }
        absence.retain(activeIds);
    }

    /** 只接受成功、完整、类型正确的快照；错误不等同空节点。
     * @param node 节点标识
     * @return 完整 UUID 集合，失败为空
     */
    private Set<String> snapshot(String node) {
        try {
            var result=client.channelSnapshot(node);
            if (result==null || !result.isSuccess() || !(result.getData() instanceof Map<?,?> data)
                    || !Boolean.TRUE.equals(data.get("complete")) || !(data.get("channel_uuids") instanceof List<?> rows)) return null;
            Set<String> channels=new HashSet<>();
            for (Object row:rows) {
                if (!(row instanceof String uuid) || uuid.isBlank() || !channels.add(uuid)) return null;
            }
            return channels;
        } catch (RuntimeException failure) { return null; }
    }
}
