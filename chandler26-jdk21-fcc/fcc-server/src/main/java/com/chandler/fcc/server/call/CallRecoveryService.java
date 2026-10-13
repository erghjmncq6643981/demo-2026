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
import org.springframework.scheduling.annotation.Scheduled;

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
     * @throws RuntimeException 分页查询数据库不可用；单条损坏会话被隔离
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
                try {
                    if (restoreRow(after, row)) mapper.resolveRecoveryIssue(after);
                } catch (RuntimeException failure) {
                    log.error("[通话恢复] 隔离损坏会话，继续恢复后续通话 callId={} type={}",
                        after, failure.getClass().getSimpleName());
                    try { mapper.recordRecoveryIssue(after, failure.getClass().getSimpleName()); }
                    catch (RuntimeException auditFailure) {
                        log.error("[通话恢复] 隔离审计写入失败 callId={} type={}",
                            after, auditFailure.getClass().getSimpleName());
                    }
                }
            }
        }
    }

    /** 定期重读持久事实；配置修复后可恢复，绝不自动重拨或伪造结束。 */
    @Scheduled(fixedDelayString = "${fcc.call.recovery-retry-ms:300000}", initialDelayString = "${fcc.call.recovery-retry-ms:300000}")
    public void retryRecovery() {
        restore();
    }

    /**
     * 恢复单条持久会话。
     *
     * @param callId 数据库业务通话 ID
     * @param row 会话摘要行
     * @return 已恢复或已存在；非固定模板返回 false，不关闭异常记录
     * @throws IllegalStateException 持久上下文损坏
     */
    private boolean restoreRow(long callId, Map<String, Object> row) {
        Object ctrlIdValue = row.get("ctrlId");
        if (ctrlIdValue == null) throw new IllegalStateException("缺少控制标识");
        if (sessions.getByCtrlUuid(ctrlIdValue.toString()).isPresent()) return true;
        Object attributes = row.get("attributes");
        if (attributes == null) {
            return false;
        }
        try {
            Map<String, Object> data = json.readValue(
                attributes.toString(),
                new TypeReference<HashMap<String, Object>>() {}
            );
            if (!data.containsKey("runtimeTemplate")) {
                return false;
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
            Object transferChannel = data.get("transferChannelUuid");
            if (transferChannel instanceof String channel && !channel.isBlank()) {
                sessions.bindChannel(channel, call.getCtrlId());
            }
            return true;
        } catch (Exception failure) {
            throw new IllegalStateException("无法恢复通话 " + callId, failure);
        }
    }
}
