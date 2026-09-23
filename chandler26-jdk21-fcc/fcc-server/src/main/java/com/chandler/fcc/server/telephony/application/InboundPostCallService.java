package com.chandler.fcc.server.telephony.application;

import com.chandler.fcc.common.dto.command.FNodeHangupDTO;
import com.chandler.fcc.common.dto.command.FNodePlayDTO;
import com.chandler.fcc.common.dto.command.FNodeReadDTMFDTO;
import com.chandler.fcc.common.dto.command.MediaInfo;
import com.chandler.fcc.common.entity.CallInfoBO;
import com.chandler.fcc.common.enums.FlowActionType;
import com.chandler.fcc.common.protocol.FNodeDtmfPostAction;
import com.chandler.fcc.common.protocol.FccEventField;
import com.chandler.fcc.common.protocol.FNodeMediaType;
import com.chandler.fcc.common.protocol.FNodePlayPostAction;
import com.chandler.fcc.server.flow.application.FlowActionExecutionService;
import com.chandler.fcc.server.infrastructure.persistence.service.CallPersistenceService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 执行呼入通话后的预设服务评价和结束语音流程。
 *
 * <p>只有坐席先离开已接通通话时才保留客户话道进入评价；客户先挂机时直接结束。
 * 评价分数写入通话事实，结束语音由 Sidecar 原子执行“播放后挂机”。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InboundPostCallService {

    private static final String DATA_RATING_PENDING = "serviceRatingPending";
    private static final String DATA_RATING_DEADLINE = "serviceRatingDeadline";
    private static final String DATA_CLOSING_PENDING = "closingVoicePending";
    private static final String DATA_CLOSING_ACCEPTED = "closingVoiceAccepted";
    private static final int RATING_TIMEOUT_MILLIS = 10_000;

    private final FlowActionExecutionService actions;
    private final CallPersistenceService persistence;

    @Value("${fcc.inbound.service-rating-prompt-file:}")
    private String ratingPromptFile;

    @Value("${fcc.inbound.closing-prompt-file:}")
    private String closingPromptFile;

    /**
     * 在客户话道上开始一次服务评价。
     *
     * @param call 已接通且坐席侧刚结束的呼入通话
     * @return 已经进入评价流程时返回 {@code true}；未配置预设音频时返回 {@code false}
     */
    public boolean begin(CallInfoBO call) {
        if (ratingPromptFile.isBlank() || closingPromptFile.isBlank()) {
            log.warn("[服务评价] 未配置评价或结束语音，跳过评价 callId={}", call.getCallId());
            return false;
        }
        if (call.getData().putIfAbsent(DATA_RATING_PENDING, true) != null) {
            return true;
        }
        call.putData(DATA_RATING_DEADLINE, System.currentTimeMillis() + RATING_TIMEOUT_MILLIS);
        persistence.saveOrUpdateSession(call);
        try {
            actions.executeFNode(
                call,
                FlowActionType.COLLECT_SERVICE_RATING,
                FNodeReadDTMFDTO.builder()
                    .ctrlUuid(call.getCtrlId())
                    .uuid(call.getGuestChannelUuid())
                    .media(
                        MediaInfo.builder()
                            .type(FNodeMediaType.FILE)
                            .data(ratingPromptFile)
                            .build()
                    )
                    .minDigits(1)
                    .maxDigits(1)
                    .tries(1)
                    .timeout(RATING_TIMEOUT_MILLIS)
                    .digitTimeout(1_000)
                    .terminators("#")
                    .regex("^[1-5]$")
                    .actionAfter(FNodeDtmfPostAction.PARK)
                    .build(),
                "service-rating-" + call.getCallId()
            );
        } catch (RuntimeException failure) {
            call.getData().remove(DATA_RATING_PENDING);
            call.getData().remove(DATA_RATING_DEADLINE);
            persistence.saveOrUpdateSession(call);
            throw failure;
        }
        return true;
    }

    /**
     * 保存客户提交的评价并进入结束语音。
     *
     * @param call 当前呼入通话
     * @param params 标准 DTMF 事件
     * @return 当前事件已由评价流程消费时返回 {@code true}
     */
    public boolean digits(CallInfoBO call, JsonNode params) {
        if (call.getData().containsKey(DATA_CLOSING_PENDING)) {
            if (!call.getData().containsKey(DATA_CLOSING_ACCEPTED)) {
                playClosingAndHangup(call);
            }
            return true;
        }
        if (
            !call.getData().containsKey(DATA_RATING_PENDING) ||
            !call.getGuestChannelUuid().equals(
                params.path(FccEventField.CHANNEL_UUID.getWireName()).asText()
            )
        ) {
            return false;
        }
        String digit = params.path(FccEventField.DIGIT.getWireName()).asText();
        if (!digit.matches("[1-5]")) {
            return true;
        }
        actions.executeInternal(
            call,
            FlowActionType.PERSIST_SERVICE_RATING,
            () -> {
                call.setEvaluationScore(Integer.parseInt(digit));
                call.getData().remove(DATA_RATING_PENDING);
                call.getData().remove(DATA_RATING_DEADLINE);
                persistence.saveOrUpdateSession(call);
                return call.getEvaluationScore();
            }
        );
        playClosingAndHangup(call);
        return true;
    }

    /**
     * 结束超过等待时限但没有有效按键的评价。
     *
     * @param call 当前呼入通话
     * @return 本次检查触发结束语音时返回 {@code true}
     */
    public boolean expire(CallInfoBO call) {
        if (call.getData().containsKey(DATA_CLOSING_PENDING)) {
            if (!call.getData().containsKey(DATA_CLOSING_ACCEPTED)) {
                playClosingAndHangup(call);
            }
            return true;
        }
        Object deadline = call.getData().get(DATA_RATING_DEADLINE);
        if (
            !call.getData().containsKey(DATA_RATING_PENDING) ||
            !(deadline instanceof Number number) ||
            System.currentTimeMillis() <= number.longValue()
        ) {
            return false;
        }
        call.getData().remove(DATA_RATING_PENDING);
        call.getData().remove(DATA_RATING_DEADLINE);
        persistence.saveOrUpdateSession(call);
        playClosingAndHangup(call);
        return true;
    }

    /**
     * 播放预设结束语音并由 Sidecar 在播放完成后正常挂机。
     *
     * @param call 当前呼入通话
     */
    private void playClosingAndHangup(CallInfoBO call) {
        call.putData(DATA_CLOSING_PENDING, true);
        persistence.saveOrUpdateSession(call);
        if (!closingPromptFile.isBlank()) {
            actions.executeFNode(
                call,
                FlowActionType.PLAY_CLOSING_VOICE,
                FNodePlayDTO.builder()
                    .ctrlUuid(call.getCtrlId())
                    .uuid(call.getGuestChannelUuid())
                    .media(
                        MediaInfo.builder()
                            .type(FNodeMediaType.FILE)
                            .data(closingPromptFile)
                            .build()
                    )
                    .actionAfter(FNodePlayPostAction.HANGUP)
                    .build(),
                "closing-voice-" + call.getCallId()
            );
            call.putData(DATA_CLOSING_ACCEPTED, true);
            persistence.saveOrUpdateSession(call);
            return;
        }
        actions.executeFNode(
            call,
            FlowActionType.HANGUP_CALL,
            FNodeHangupDTO.builder()
                .ctrlUuid(call.getCtrlId())
                .uuid(call.getGuestChannelUuid())
                .cause("NORMAL_CLEARING")
                .build(),
            "rating-hangup-" + call.getCallId()
        );
    }
}
