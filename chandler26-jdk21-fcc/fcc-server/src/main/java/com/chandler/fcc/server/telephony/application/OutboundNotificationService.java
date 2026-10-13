package com.chandler.fcc.server.telephony.application;


import com.chandler.fcc.common.dto.command.FNodePlayDTO;
import com.chandler.fcc.common.dto.command.FNodeReadDTMFDTO;
import com.chandler.fcc.common.dto.command.MediaInfo;
import com.chandler.fcc.common.entity.CallInfoBO;
import com.chandler.fcc.common.enums.AutoDialTaskType;
import com.chandler.fcc.common.enums.CallStageState;
import com.chandler.fcc.common.enums.FlowActionType;
import com.chandler.fcc.common.protocol.FNodeDtmfPostAction;
import com.chandler.fcc.common.protocol.FNodeMediaType;
import com.chandler.fcc.common.protocol.FNodePlayPostAction;
import com.chandler.fcc.common.protocol.FccEventField;
import com.chandler.fcc.common.protocol.FccCommandResultStatus;
import com.chandler.fcc.server.command.FccClient;
import com.chandler.fcc.server.flow.application.FlowActionExecutionService;
import com.chandler.fcc.server.infrastructure.persistence.service.CallPersistenceService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import static com.chandler.fcc.server.telephony.application.OutboundRuntimeSupport.TEMPLATE_NOTIFICATION;
import static com.chandler.fcc.server.telephony.application.OutboundRuntimeSupport.DATA_TERMINAL;

/** 通知外呼播放与最终收号结果；受理不等于播放或确认完成。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OutboundNotificationService {
    private final CallPersistenceService persistence;
    private final FccClient client;
    private final FlowActionExecutionService flowActions;

    /**
     * 向通知外呼客户播放媒体，问卷类型收取确认按键，通知类型播放完成后直接结束。
     *
     * @param call 当前通知通话
     */
    public void startNotificationPrompt(CallInfoBO call) {
        if (call.getData().putIfAbsent("notificationStarted", true) != null) {
            return;
        }
        call.setStageState(CallStageState.CONNECTED);
        persistence.saveOrUpdateSession(call);
        String taskType = call.getDataStr("taskType", AutoDialTaskType.NOTIFY.name());
        if (AutoDialTaskType.SURVEY.name().equalsIgnoreCase(taskType)) {
            flowActions.executeFNode(
                call,
                FlowActionType.READ_DTMF,
                FNodeReadDTMFDTO.builder()
                    .ctrlUuid(call.getCtrlId())
                    .uuid(call.getGuestChannelUuid())
                    .media(
                        MediaInfo.builder()
                            .type(FNodeMediaType.TEXT)
                            .data(call.getDataStr("notificationText", ""))
                            .build()
                    )
                    .minDigits(1)
                    .maxDigits(1)
                    .tries(1)
                    .timeout(notificationTimeoutMillis(call))
                    .digitTimeout(2_000)
                    .terminators("#")
                    .regex("^[" + call.getDataStr("confirmDigit", "1") + "]$")
                    .actionAfter(FNodeDtmfPostAction.PARK)
                    .build(),
                "notification-dtmf-" + call.getCallId()
            );
        } else {
            flowActions.executeFNode(
                call,
                FlowActionType.PLAY_MEDIA,
                FNodePlayDTO.builder()
                    .ctrlUuid(call.getCtrlId())
                    .uuid(call.getGuestChannelUuid())
                    .media(
                        MediaInfo.builder()
                            .type(FNodeMediaType.TEXT)
                            .data(call.getDataStr("notificationText", ""))
                            .build()
                    )
                    .actionAfter(FNodePlayPostAction.PARK)
                    .build(),
                "notification-play-" + call.getCallId()
            );
        }
    }

    /**
     * 读取已固定到通话快照的确认等待时间。
     *
     * @param call 当前通知通话
     * @return 毫秒超时时间
     */
    private int notificationTimeoutMillis(CallInfoBO call) {
        Object value = call.getData().get("notificationTimeoutSeconds");
        int seconds = value instanceof Number number
            ? number.intValue()
            : Integer.parseInt(value == null ? "10" : value.toString());
        return Math.clamp(seconds, 3, 60) * 1_000;
    }

    /**
     * 使用通知外呼 ReadDTMF 的最终指令结果保存客户确认。
     *
     * @param call 当前通话
     * @param params Sidecar 规范指令结果参数
     * @return 当前结果是否属于通知外呼固定模型
     */
    public boolean commandResult(CallInfoBO call, JsonNode params) {
        String template = call.getDataStr("runtimeTemplate", "");
        if (!TEMPLATE_NOTIFICATION.equals(template)) {
            return false;
        }
        synchronized (call) {
            if (call.getData().containsKey(DATA_TERMINAL)) {
                return true;
            }
            if (TEMPLATE_NOTIFICATION.equals(template)) {
                String cmdId = params.path(FccEventField.COMMAND_ID.getWireName()).asText();
                if (("notification-play-" + call.getCallId()).equals(cmdId)) {
                    if (call.getData().putIfAbsent("notificationHangupRequested", true) == null) {
                        persistence.saveOrUpdateSession(call);
                        client.hangup(
                            call.getCtrlId(),
                            call.getGuestChannelUuid(),
                            "NORMAL_CLEARING"
                        );
                    }
                    return true;
                }
                if (!("notification-dtmf-" + call.getCallId()).equals(cmdId)) {
                    return true;
                }
                if (call.getData().containsKey("notificationHangupRequested")) {
                    return true;
                }
                boolean succeeded = FccCommandResultStatus.fromWireValue(
                    params.path(FccEventField.COMMAND_STATUS.getWireName()).asText()
                ) == FccCommandResultStatus.SUCCEEDED;
                String digit = params.path(FccEventField.RESULT.getWireName()).path("dtmf").asText();
                if (succeeded && call.getDataStr("confirmDigit", "1").equals(digit)) {
                    flowActions.executeInternal(
                        call,
                        FlowActionType.PERSIST_CONFIRMATION_AND_HANGUP,
                        () -> {
                            call.putData("notificationConfirmed", true);
                            persistence.saveOrUpdateSession(call);
                            return true;
                        }
                    );
                }
                if (call.getData().putIfAbsent("notificationHangupRequested", true) == null) {
                    persistence.saveOrUpdateSession(call);
                    client.hangup(
                        call.getCtrlId(),
                        call.getGuestChannelUuid(),
                        succeeded ? "NORMAL_CLEARING" : "NORMAL_TEMPORARY_FAILURE"
                    );
                }
            }
        }
        return true;
    }

}
