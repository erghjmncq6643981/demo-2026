package com.chandler.fcc.server.agent.application;

import com.chandler.fcc.common.dto.WsMessageDTO;
import com.chandler.fcc.common.enums.WsMessageTypeEnum;
import com.chandler.fcc.server.agent.infrastructure.AgentRuntimeMapper;
import com.chandler.fcc.server.websocket.service.AgentWebSocketService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 坐席参与生命周期独立于客户 Call；Leg 结束后恢复登录态对应的可接单状态。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AgentLegLifecycleService {
    private final AgentRuntimeMapper agents;
    private final AgentWebSocketService websocket;

    /**
     * 在源结束事实事务内释放单坐席，提交后广播，不等待客户评价或整个 Call 结束。
     * @param callId 原通话标识
     * @param channelUuid 已结束 Leg 标识
     */
    public void releaseEndedLeg(Long callId, String channelUuid) {
        if (agents.releaseEndedLeg(callId, channelUuid) == 0) return;
        log.info("[坐席释放] 坐席话道结束，独立释放占用 callId={} channelUuid={}", callId, channelUuid);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                /** 在结束事实提交后通知坐席；通知失败不回滚持久事实。 */
                @Override
                public void afterCommit() {
                    notifyReleased(callId, channelUuid);
                }
            });
        } else {
            notifyReleased(callId, channelUuid);
        }
    }

    /**
     * 批量读状态后通知已释放坐席，新通话已占用的坐席不收到旧空闲通知。
     * @param callId 原通话标识
     * @param channelUuid 原话道标识
     */
    private void notifyReleased(Long callId, String channelUuid) {
        try {
            for (Map<String, Object> row : agents.releasedLegAgents(callId, channelUuid)) {
                String owner = row.get("workNo").toString();
                websocket.sendToWorkNo(owner, WsMessageDTO.of(
                    WsMessageTypeEnum.AGENT_PRESENCE_CHANGE.getCode(), owner, callId.toString(),
                    Map.of("workStatus", row.get("workStatus"), "reason", "AGENT_LEG_ENDED")));
            }
        } catch (RuntimeException failure) {
            log.warn("[坐席释放] 状态通知待客户端重取 callId={} type={}", callId, failure.getClass().getSimpleName());
        }
    }
}
