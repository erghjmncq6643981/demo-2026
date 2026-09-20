package com.chandler.fcc.server.event.handler;

import com.chandler.fcc.common.entity.CallInfoBO;
import com.chandler.fcc.common.protocol.FccEventMethods;
import com.chandler.fcc.common.recording.RecordingPathLayout;
import com.chandler.fcc.server.call.CallSessionManager;
import com.chandler.fcc.server.infrastructure.persistence.entity.CallRecordingEntity;
import com.chandler.fcc.server.infrastructure.persistence.service.CallPersistenceService;
import com.chandler.fcc.server.recording.RecordingPathResolver;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 根据录音生命周期事件补齐并结算录音元数据。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RecordingEventHandler implements FccEventHandler {

    private final CallSessionManager sessions;
    private final CallPersistenceService persistence;
    private final RecordingPathResolver pathResolver;

    /**
     * 判断是否为录音生命周期事件。
     *
     * @param method 标准事件方法名
     * @return 是否支持
     */
    @Override
    public boolean supports(String method) {
        return FccEventMethods.RECORDING.equalsIgnoreCase(method);
    }

    /**
     * 以业务通话标识关联录音，拒绝生成没有通话归属的替代标识。
     *
     * @param params 录音事件参数
     */
    @Override
    public void handle(JsonNode params) {
        String controlId = params.path("ctrl_uuid").asText(null);
        String channelUuid = params.path("uuid").asText(null);
        CallInfoBO call = sessions
            .getByCtrlUuid(controlId)
            .or(() -> sessions.getByChannelUuid(channelUuid))
            .orElse(null);
        if (call == null) {
            log.warn("[录音事件] 无法定位业务通话 ctrlId={} channelUuid={}", controlId, channelUuid);
            return;
        }

        String recordPath = params.path("file_path").asText(null);
        if (recordPath == null || recordPath.isBlank()) {
            recordPath = pathResolver
                .resolve(call.getCallId(), LocalDateTime.now(ZoneOffset.UTC), null)
                .absolutePath();
        }

        boolean stopping = "stop".equalsIgnoreCase(params.path("action").asText());
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        persistence.upsertRecording(
            CallRecordingEntity.builder()
                .recordingId(RecordingPathLayout.recordingIdOf(call.getCallId()))
                .callId(CallPersistenceService.parseNumericId(call.getCallId()))
                .nodeId(params.path("node_id").asText(null))
                .status(stopping ? "COMPLETED" : "RECORDING")
                .storageType("LOCAL")
                .objectKey(recordPath)
                .mediaFormat(mediaFormat(recordPath))
                .durationMs(durationMillis(params))
                .sizeBytes(stopping ? fileSize(recordPath) : null)
                .startedAt(now)
                .completedAt(stopping ? now : null)
                .build()
        );
    }

    /**
     * 读取录音时长并转换为毫秒。
     *
     * @param params 录音事件参数
     * @return 时长毫秒，缺失时为空
     */
    private Long durationMillis(JsonNode params) {
        JsonNode seconds = params.get("seconds");
        return seconds == null || seconds.isNull() ? null : seconds.asLong() * 1000L;
    }

    /**
     * 读取已落盘录音文件大小。
     *
     * @param recordPath 录音路径
     * @return 正文件大小，不可读时为空
     */
    private Long fileSize(String recordPath) {
        try {
            Path file = Path.of(recordPath.trim());
            if (!Files.isRegularFile(file)) return null;
            long size = Files.size(file);
            return size > 0 ? size : null;
        } catch (Exception failure) {
            log.debug("[录音事件] 无法读取文件大小 path={}", recordPath);
            return null;
        }
    }

    /**
     * 从文件路径推断媒体封装格式。
     *
     * @param recordPath 录音路径
     * @return 小写扩展名，缺省为 wav
     */
    private String mediaFormat(String recordPath) {
        int dot = recordPath.lastIndexOf('.');
        return dot < 0 || dot == recordPath.length() - 1
            ? "wav"
            : recordPath.substring(dot + 1).toLowerCase();
    }
}
