package com.chandler.fcc.server.call.application;

import com.chandler.fcc.server.agent.infrastructure.AgentParticipationMapper;

import com.chandler.fcc.server.call.domain.CallEndDecision;
import com.chandler.fcc.server.call.infrastructure.persistence.data.CallCleanupCandidate;
import com.chandler.fcc.server.call.infrastructure.persistence.mapper.CallEndMapper;
import com.chandler.fcc.server.outbound.application.DialJobReconciliationService;
import com.chandler.fcc.server.agent.application.AgentLegLifecycleService;
import com.chandler.fcc.common.entity.CallInfoBO;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 实时终态与日终对账共用的幂等收尾服务；事务内仅执行数据库操作。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CallFinalizationService {
    private final CallEndMapper mapper;
    private final DialJobReconciliationService dialJobs;
    private final AgentLegLifecycleService agentLegs;
    private final AgentParticipationMapper participation;

    /**
     * 用真实话道身份解析持久通话，不创建新的通话或恢复拨号副作用。
     * @param ctrlId 可选控制标识
     * @param channelUuid 话道标识
     * @param nodeId 源节点归属
     * @return 仅含身份的通话上下文，未匹配时为空
     */
    public CallInfoBO resolveCall(String ctrlId, String channelUuid, String nodeId) {
        if (channelUuid == null || nodeId == null) return null;
        CallCleanupCandidate row = mapper.resolveChannel(ctrlId, channelUuid, nodeId);
        return row == null ? null : CallInfoBO.builder().callId(row.getId().toString())
            .ctrlId(row.getCtrlId()).nodeId(nodeId).build();
    }

    /**
     * 持久化一个源 DESTROY 事件；不会因单话道结束而释放整个坐席或终止评价。
     * @param callId 已解析的通话标识
     * @param params 标准 Channel 结束事件
     */
    @Transactional(rollbackFor = Exception.class)
    public void recordChannelEnd(String callId, JsonNode params) {
        // Serialize Leg endings on the aggregate before testing remaining participation.
        // Without this lock, two concurrent last-Leg events can both see the other as live.
        if (mapper.lockCalls(List.of(Long.valueOf(callId))).isEmpty()) return;
        LocalDateTime end = LocalDateTime.ofInstant(Instant.ofEpochMilli(CallEndTime.epochMillis(params)), ZoneOffset.UTC);
        mapper.recordLegEnd(Long.valueOf(callId), params.path("uuid").asText(), params.path("node_id").asText(),
            end, params.path("cause").asText("NORMAL_CLEARING"));
        participation.synchronizeEnded(List.of(Long.valueOf(callId)));
        agentLegs.releaseEndedLeg(Long.valueOf(callId), params.path("uuid").asText());
    }

    /**
     * 为已落盘的 Call 终态补齐关联收尾，不推断其他尚存话道的结束。
     * @param callId 已保存的通话主键
     */
    @Transactional(rollbackFor = Exception.class)
    public void cleanupEndedCall(Long callId) {
        List<CallCleanupCandidate> rows = mapper.lockCalls(List.of(callId));
        List<Long> ids = rows.stream().filter(row -> row.getEndedAt() != null
            && Set.of("NORMAL_END", "ERROR_END").contains(row.getStatus()))
            .map(CallCleanupCandidate::getId).toList();
        if (!ids.isEmpty()) cleanup(ids, false);
    }

    /**
     * 批量提交对账决策；检查后发生事件更新的通话留待下次对账。
     * @param decisions 事务外完成证据校验的决策
     * @return 实际收尾的通话主键
     * @throws OptimisticLockingFailureException 锁定后更新数量仍不一致
     */
    @Transactional(rollbackFor = Exception.class)
    public List<Long> repair(List<CallEndDecision> decisions) {
        if (decisions.isEmpty()) return List.of();
        List<Long> ids = decisions.stream().map(CallEndDecision::callId).distinct().sorted().toList();
        Map<Long, CallCleanupCandidate> current = mapper.lockCalls(ids).stream()
            .collect(Collectors.toMap(CallCleanupCandidate::getId, Function.identity()));
        List<CallEndDecision> valid = decisions.stream().filter(decision ->
            current.containsKey(decision.callId())
                && Objects.equals(current.get(decision.callId()).getVersion(), decision.version())).toList();
        if (valid.size() != decisions.size()) {
            log.warn("[通话兜底] 并发更新，延后对账 count={}", decisions.size() - valid.size());
        }
        if (valid.isEmpty()) return List.of();
        if (mapper.completeCalls(valid) != valid.size()) {
            throw new OptimisticLockingFailureException("通话兜底终态更新数量不一致");
        }
        List<Long> completed = valid.stream().map(CallEndDecision::callId).toList();
        List<Long> absent = valid.stream().filter(CallEndDecision::channelsAbsent)
            .map(CallEndDecision::callId).toList();
        if (!absent.isEmpty()) mapper.closeLegs(absent, true);
        cleanup(completed, false);
        for (CallEndDecision decision : valid) {
            log.info("[通话兜底] 结束事实已修复 callId={} evidence={} cause={}",
                decision.callId(), decision.evidence(), decision.cause());
        }
        return completed;
    }

    /**
     * 批量收尾相关事实，录音仅标记待对账，坐席仅按原通话释放。
     * @param ids 已确认结束的通话
     * @param channelsAbsent 是否确认所有话道不存在
     */
    private void cleanup(List<Long> ids, boolean channelsAbsent) {
        mapper.closeLegs(ids, channelsAbsent);
        participation.synchronizeEnded(ids);
        mapper.closeBridgeMembers(ids);
        mapper.closeBridges(ids);
        mapper.closeSteps(ids);
        mapper.closeFlows(ids);
        mapper.releaseAgents(ids);
        mapper.markRecordingsUnknown(ids);
        dialJobs.reconcileEndedCalls(ids);
    }
}
