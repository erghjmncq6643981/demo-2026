package com.chandler.fcc.server.recording.application;

import com.chandler.fcc.common.dto.command.FNodeRecordDTO;
import com.chandler.fcc.common.entity.CallInfoBO;
import com.chandler.fcc.common.enums.FlowActionType;
import com.chandler.fcc.common.protocol.FNodeRecordAction;
import com.chandler.fcc.common.recording.RecordingPathLayout;
import com.chandler.fcc.server.flow.application.FlowActionExecutionService;
import com.chandler.fcc.server.infrastructure.persistence.entity.CallRecordingEntity;
import com.chandler.fcc.server.infrastructure.persistence.service.CallPersistenceService;
import com.chandler.fcc.server.recording.RecordingPathResolver;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 负责通话录音指令、共享路径和录音元数据之间的一致性。
 *
 * <p>服务先登记录音意图，再使用稳定命令标识下发 FNode 指令。最终完成状态只由
 * {@code Event.Recording} 补齐，指令受理不会被误认为文件已经落盘。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CallRecordingService {

    private static final String DATA_RECORDING_PATH = "recordingPath";
    private static final String DATA_RECORDING_STARTED = "recordingStarted";
    private static final String DATA_RECORDING_STOPPED = "recordingStopped";

    private final RecordingPathResolver pathResolver;
    private final CallPersistenceService persistence;
    private final FlowActionExecutionService actions;

    /**
     * 为已桥接通话幂等请求开始录音。
     *
     * @param call 已进入双向媒体阶段的业务通话
     */
    public void start(CallInfoBO call) {
        if (call.getData().putIfAbsent(DATA_RECORDING_STARTED, true) != null) {
            return;
        }
        RecordingPathResolver.RecordingTarget target = pathResolver.resolve(
            call.getCallId(),
            LocalDateTime.now(ZoneOffset.UTC),
            null
        );
        call.putData(DATA_RECORDING_PATH, target.absolutePath());
        persistence.saveOrUpdateSession(call);
        try {
            persistence.upsertRecording(
                recording(call, target, "REQUESTED", target.startedAt(), null)
            );
            actions.executeFNode(
                call,
                FlowActionType.START_RECORDING,
                FNodeRecordDTO.builder()
                    .ctrlUuid(call.getCtrlId())
                    .uuid(call.getGuestChannelUuid())
                    .action(FNodeRecordAction.START)
                    .path(target.absolutePath())
                    .build(),
                "record-start-" + call.getCallId()
            );
        } catch (RuntimeException failure) {
            call.getData().remove(DATA_RECORDING_STARTED);
            persistence.saveOrUpdateSession(call);
            throw failure;
        }
    }

    /**
     * 为正在录音的通话幂等请求停止录音。
     *
     * <p>停止请求失败不会阻止通话终态与坐席释放；稳定命令及录音意图仍可由恢复任务
     * 和 Sidecar 事件对账。</p>
     *
     * @param call 即将进入终态或评价阶段的业务通话
     */
    public void stop(CallInfoBO call) {
        String path = call.getDataStr(DATA_RECORDING_PATH, null);
        if (
            path == null ||
            call.getData().putIfAbsent(DATA_RECORDING_STOPPED, true) != null
        ) {
            return;
        }
        persistence.saveOrUpdateSession(call);
        try {
            actions.executeFNode(
                call,
                FlowActionType.STOP_RECORDING,
                FNodeRecordDTO.builder()
                    .ctrlUuid(call.getCtrlId())
                    .uuid(call.getGuestChannelUuid())
                    .action(FNodeRecordAction.STOP)
                    .path(path)
                    .build(),
                "record-stop-" + call.getCallId()
            );
        } catch (RuntimeException failure) {
            call.getData().remove(DATA_RECORDING_STOPPED);
            persistence.saveOrUpdateSession(call);
            persistence.upsertRecording(
                CallRecordingEntity.builder()
                    .recordingId(RecordingPathLayout.recordingIdOf(call.getCallId()))
                    .callId(CallPersistenceService.parseNumericId(call.getCallId()))
                    .nodeId(call.getNodeId())
                    .status("UNKNOWN")
                    .storageType("LOCAL")
                    .objectKey(path)
                    .build()
            );
            log.warn(
                "[通话录音] 停止结果待对账 callId={} type={}",
                call.getCallId(),
                failure.getClass().getSimpleName()
            );
        }
    }

    /**
     * 构造录音意图事实。
     *
     * @param call 业务通话
     * @param target 共享录音目标
     * @param status 当前录音状态
     * @param startedAt 请求开始时间
     * @param completedAt 完成时间
     * @return 可幂等合并的录音实体
     */
    private CallRecordingEntity recording(
        CallInfoBO call,
        RecordingPathResolver.RecordingTarget target,
        String status,
        LocalDateTime startedAt,
        LocalDateTime completedAt
    ) {
        return CallRecordingEntity.builder()
            .recordingId(target.recordingId())
            .callId(CallPersistenceService.parseNumericId(call.getCallId()))
            .nodeId(call.getNodeId())
            .status(status)
            .storageType("LOCAL")
            .objectKey(target.absolutePath())
            .mediaFormat(target.mediaFormat())
            .startedAt(startedAt)
            .completedAt(completedAt)
            .build();
    }
}
