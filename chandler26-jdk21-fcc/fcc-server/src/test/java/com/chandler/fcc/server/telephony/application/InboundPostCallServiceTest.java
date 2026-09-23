package com.chandler.fcc.server.telephony.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chandler.fcc.common.dto.command.FNodePlayDTO;
import com.chandler.fcc.common.entity.CallInfoBO;
import com.chandler.fcc.common.enums.FlowActionType;
import com.chandler.fcc.common.protocol.FNodePlayPostAction;
import com.chandler.fcc.server.flow.application.FlowActionExecutionService;
import com.chandler.fcc.server.flow.application.executor.InternalFlowActionInvocation;
import com.chandler.fcc.server.infrastructure.persistence.service.CallPersistenceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 呼入服务评价和结束语音测试。
 */
class InboundPostCallServiceTest {

    /**
     * 有效评价必须写入通话事实，并使用播放完成后挂机的结束语音命令。
     */
    @Test
    void persistsRatingAndPlaysClosingVoiceBeforeHangup() throws Exception {
        FlowActionExecutionService actions = mock(FlowActionExecutionService.class);
        CallPersistenceService persistence = mock(CallPersistenceService.class);
        when(actions.executeInternal(any(), any(), any())).thenAnswer(invocation -> {
            InternalFlowActionInvocation action = invocation.getArgument(2);
            action.invoke();
            return null;
        });
        InboundPostCallService service = new InboundPostCallService(actions, persistence);
        ReflectionTestUtils.setField(service, "ratingPromptFile", "/media/rating.wav");
        ReflectionTestUtils.setField(service, "closingPromptFile", "/media/closing.wav");
        CallInfoBO call = CallInfoBO.builder()
            .callId("9001")
            .ctrlId("ctrl-9001")
            .guestChannelUuid("customer-channel")
            .data(new HashMap<>())
            .build();

        assertTrue(service.begin(call));
        var event = new ObjectMapper().readTree(
            "{\"uuid\":\"customer-channel\",\"digit\":\"5\"}"
        );
        assertTrue(service.digits(call, event));
        assertEquals(5, call.getEvaluationScore());

        ArgumentCaptor<FNodePlayDTO> command = ArgumentCaptor.forClass(FNodePlayDTO.class);
        verify(actions).executeFNode(
            eq(call),
            eq(FlowActionType.PLAY_CLOSING_VOICE),
            command.capture(),
            eq("closing-voice-9001")
        );
        assertEquals(FNodePlayPostAction.HANGUP, command.getValue().getActionAfter());
        assertEquals("/media/closing.wav", command.getValue().getMedia().getData());
        verify(persistence, times(4)).saveOrUpdateSession(call);
    }
}
