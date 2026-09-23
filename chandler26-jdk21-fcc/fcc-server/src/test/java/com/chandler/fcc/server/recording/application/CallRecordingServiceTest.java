package com.chandler.fcc.server.recording.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chandler.fcc.common.dto.command.FNodeRecordDTO;
import com.chandler.fcc.common.entity.CallInfoBO;
import com.chandler.fcc.common.enums.FlowActionType;
import com.chandler.fcc.server.flow.application.FlowActionExecutionService;
import com.chandler.fcc.server.infrastructure.persistence.service.CallPersistenceService;
import com.chandler.fcc.server.recording.RecordingPathResolver;
import java.time.LocalDateTime;
import java.util.HashMap;
import org.junit.jupiter.api.Test;

/**
 * 通话录音命令与元数据边界测试。
 */
class CallRecordingServiceTest {

    /**
     * 重复开始或停止只能下发一次对应的稳定命令。
     */
    @Test
    void startsAndStopsRecordingIdempotently() {
        RecordingPathResolver paths = mock(RecordingPathResolver.class);
        CallPersistenceService persistence = mock(CallPersistenceService.class);
        FlowActionExecutionService actions = mock(FlowActionExecutionService.class);
        LocalDateTime startedAt = LocalDateTime.of(2026, 9, 23, 1, 0);
        when(paths.resolve(eq("9001"), any(), eq(null))).thenReturn(
            new RecordingPathResolver.RecordingTarget(
                "rec-9001",
                "/recordings/9001.wav",
                "wav",
                startedAt
            )
        );
        CallRecordingService service = new CallRecordingService(paths, persistence, actions);
        CallInfoBO call = CallInfoBO.builder()
            .callId("9001")
            .ctrlId("ctrl-9001")
            .guestChannelUuid("customer-channel")
            .data(new HashMap<>())
            .build();

        service.start(call);
        service.start(call);
        service.stop(call);
        service.stop(call);

        verify(actions, times(1)).executeFNode(
            eq(call),
            eq(FlowActionType.START_RECORDING),
            any(FNodeRecordDTO.class),
            eq("record-start-9001")
        );
        verify(actions, times(1)).executeFNode(
            eq(call),
            eq(FlowActionType.STOP_RECORDING),
            any(FNodeRecordDTO.class),
            eq("record-stop-9001")
        );
        verify(persistence, times(2)).saveOrUpdateSession(call);
        verify(persistence).upsertRecording(any());
    }
}
