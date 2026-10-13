package com.chandler.fcc.server.telephony.application;

import com.chandler.fcc.server.call.application.CallEndTime;

import com.chandler.fcc.common.entity.CallInfoBO;
import com.chandler.fcc.common.enums.CallStageState;
import com.chandler.fcc.common.enums.FlowActionType;
import com.chandler.fcc.common.protocol.FccEventField;
import com.chandler.fcc.server.agent.infrastructure.AgentRuntimeMapper;
import com.chandler.fcc.server.call.CallSessionManager;
import com.chandler.fcc.server.command.FccClient;
import com.chandler.fcc.server.flow.application.FlowActionExecutionService;
import com.chandler.fcc.server.infrastructure.persistence.service.CallPersistenceService;
import com.chandler.fcc.server.recording.application.CallRecordingService;
import com.chandler.fcc.server.websocket.service.AgentWebSocketService;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import static com.chandler.fcc.server.telephony.application.OutboundRuntimeSupport.DATA_TERMINAL;
import static com.chandler.fcc.server.telephony.application.OutboundRuntimeSupport.isAgentCall;

/** 外呼终态与客户话后流程协调；保留既有释放语义，不扩展业务规则。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OutboundLifecycleCoordinator {
    private final AgentRuntimeMapper agents;
    private final CallSessionManager sessions;
    private final CallPersistenceService persistence;
    private final FccClient client;
    private final FlowActionExecutionService flowActions;
    private final TransactionTemplate transactions;
    private final CallRecordingService recordings;
    private final InboundPostCallService postCall;
    private final AgentWebSocketService websocket;

    /**
     * 幂等结算外呼并释放坐席占用。
     *
     * @param call 当前通话
     * @param template 固定模板代码
     * @param destroyedChannelUuid 已结束话道标识
     * @param params 标准化通道事件
     */
    public void finish(
        CallInfoBO call,
        String template,
        String destroyedChannelUuid,
        JsonNode params
    ) {
        FlowActionType action = isAgentCall(template)
            ? FlowActionType.FINALIZE_OUTBOUND
            : FlowActionType.FINALIZE_NOTIFICATION;
        flowActions.executeInternal(
            call,
            action,
            () -> {
                finishInternal(call, template, destroyedChannelUuid, params);
                return call.getHangupCause();
            }
        );
    }

    /**
     * 保存外呼终态、释放坐席并挂断存量对端话道。
     *
     * @param call 当前通话
     * @param template 固定模板代码
     * @param destroyedChannelUuid 已结束话道标识
     * @param params 标准化通道事件
     */
    private void finishInternal(
        CallInfoBO call,
        String template,
        String destroyedChannelUuid,
        JsonNode params
    ) {
        recordings.stop(call);
        CallStageState previousStage = call.getStageState();
        call.putData(DATA_TERMINAL, true);
        call.setHangupCause(
            params != null && params.has(FccEventField.CAUSE.getWireName())
                ? params.path(FccEventField.CAUSE.getWireName()).asText("NORMAL_CLEARING")
                : "NORMAL_CLEARING"
        );
        call.setStageState(OutboundTerminalPolicy.stageFor(call.getHangupCause()));
        call.putData("callEndedAt", CallEndTime.epochMillis(params));
        if (params != null) {
            call.setDuration(params.path(FccEventField.DURATION.getWireName()).asInt());
            call.setBillsec(params.path(FccEventField.BILL_SECONDS.getWireName()).asInt());
        }

        try {
            transactions.executeWithoutResult(status -> {
                persistence.saveOrUpdateSession(call);
                if (isAgentCall(template)) {
                    agents.release(call.getAgentWorkNo(), call.getCallId());
                }
            });
        } catch (RuntimeException failure) {
            call.getData().remove(DATA_TERMINAL);
            call.setStageState(previousStage);
            throw failure;
        }

        String peerUuid = destroyedChannelUuid != null && destroyedChannelUuid.equals(call.getAgentChannelUuid())
            ? call.getGuestChannelUuid()
            : call.getAgentChannelUuid();
        if (peerUuid != null) {
            client.hangup(call.getCtrlId(), peerUuid, "NORMAL_CLEARING");
        }
        if (isAgentCall(template) && !call.getData().containsKey("agentEndedForRating")
            && !Boolean.TRUE.equals(call.getData().get("transferSuccess"))) {
            websocket.pushCallHangup(
                call.getAgentWorkNo(),
                call.getCallId(),
                Map.of("cause", call.getHangupCause())
            );
        }
        sessions.removeSession(call.getCtrlId());
    }

    /**
     * 释放已离开的坐席，并在仍存活的客户话道上启动服务评价。
     *
     * @param call 已接通的外呼通话
     * @param template 流程模板
     * @param cause 坐席侧挂机原因
     */
    public void beginPostCall(CallInfoBO call, String template, String cause) {
        recordings.stop(call);
        if (call.getData().putIfAbsent("agentEndedForRating", true) == null) {
            transactions.executeWithoutResult(transaction -> {
                agents.release(call.getAgentWorkNo(), call.getCallId());
                persistence.saveOrUpdateSession(call);
            });
            if (call.getAgentWorkNo() != null && !Boolean.TRUE.equals(call.getData().get("transferSuccess"))) {
                websocket.pushCallHangup(
                    call.getAgentWorkNo(),
                    call.getCallId(),
                    Map.of("cause", cause == null || cause.isBlank() ? "NORMAL_CLEARING" : cause)
                );
            }
        }
        if (!postCall.begin(call)) {
            finish(call, template, call.getAgentChannelUuid(), null);
        }
    }

    /**
     * 定时处理外呼通话后的评价超时检查。
     */
    @Scheduled(fixedDelay = 2000)
    public void sweepPostCall() {
        for (var call : sessions.snapshot()) {
            String template = call.getDataStr("runtimeTemplate", "");
            if (isAgentCall(template)) {
                try {
                    synchronized (call) {
                        postCall.expire(call);
                    }
                } catch (RuntimeException ignored) {
                }
            }
        }
    }

}
