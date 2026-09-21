package com.chandler.fcc.server.call;

import com.chandler.fcc.common.entity.CallInfoBO;
import com.chandler.fcc.common.enums.CallStageState;
import com.chandler.fcc.common.enums.DirectionType;
import com.chandler.fcc.server.agent.infrastructure.AgentRuntimeMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 在消费事件之前分页恢复已持久化的固定模板会话，不自动重拨。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CallRecoveryService {

    private final AgentRuntimeMapper mapper;
    private final CallSessionManager sessions;
    private final ObjectMapper json = new ObjectMapper();

    /**
     * 恢复业务通话和双方话道关联。
     *
     * <p>节点归属不是恢复命令的前置条件；后续事件会补齐运行事实，逻辑命令由
     * Sidecar 根据话道 UUID 选择节点。</p>
     *
     * @throws IllegalStateException 持久化上下文损坏或无法构造业务会话
     */
    public void restore() {
        long after = 0;
        while (true) {
            List<Map<String, Object>> rows = mapper.active(after);
            if (rows.isEmpty()) {
                return;
            }
            for (Map<String, Object> row : rows) {
                after = ((Number) row.get("id")).longValue();
                restoreRow(after, row);
            }
        }
    }

    /**
     * 恢复单条持久会话。
     *
     * @param callId 数据库业务通话 ID
     * @param row 会话摘要行
     * @throws IllegalStateException 持久上下文损坏
     */
    private void restoreRow(long callId, Map<String, Object> row) {
        Object ctrlIdValue = row.get("ctrlId");
        if (ctrlIdValue == null || sessions.getByCtrlUuid(ctrlIdValue.toString()).isPresent()) {
            return;
        }
        Object attributes = row.get("attributes");
        if (attributes == null) {
            return;
        }
        try {
            Map<String, Object> data = json.readValue(
                attributes.toString(),
                new TypeReference<HashMap<String, Object>>() {}
            );
            if (!data.containsKey("runtimeTemplate")) {
                return;
            }
            CallInfoBO call = CallInfoBO.builder()
                .callId(String.valueOf(callId))
                .ctrlId(ctrlIdValue.toString())
                .modelKey(String.valueOf(row.get("modelType")))
                .direction(DirectionType.valueOf(String.valueOf(row.get("direction"))))
                .stageState(CallStageState.valueOf(String.valueOf(row.get("status"))))
                .callerNumber((String) row.get("caller"))
                .destinationNumber((String) row.get("destination"))
                .agentWorkNo((String) data.get("primaryWorkNo"))
                .agentExt((String) data.get("agentExt"))
                .agentChannelUuid((String) data.get("agentChannelUuid"))
                .guestChannelUuid((String) data.get("guestChannelUuid"))
                .data(data)
                .build();
            if (call.getGuestChannelUuid() == null) {
                throw new IllegalStateException("缺少客户话道归属");
            }
            sessions.registerSession(call);
        } catch (Exception failure) {
            throw new IllegalStateException("无法恢复通话 " + callId, failure);
        }
    }
}
