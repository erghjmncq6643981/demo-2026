package com.chandler.fcc.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chandler.fcc.admin.infrastructure.persistence.entity.CallRecordingEntity;
import com.chandler.fcc.admin.infrastructure.persistence.mapper.AdminCallRecordingMapper;
import com.chandler.fcc.admin.model.vo.RecordingMetaVO;
import com.chandler.fcc.common.recording.RecordingPathLayout;
import com.chandler.fcc.common.recording.RecordingStorageProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * 通话录音检索与文件定位服务
 * <p>
 * 录音地址的事实来源是 {@code fcc_call_recording.object_key}——即下发录音指令时声明、
 * 由 FreeSWITCH 写入的共享存储绝对路径。本服务负责三件事：
 * </p>
 * <ol>
 *   <li>把库内录音地址解析为可读取的本地文件路径；</li>
 *   <li>校验解析结果未越出 {@code fcc.recording.base-dir} 共享根目录 (防任意文件读取)；</li>
 *   <li>组装前端复播与下载所需的访问地址。</li>
 * </ol>
 *
 * @author Chandler
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecordingQueryService {

    /**
     * 录音资源访问基础路径
     */
    private static final String BASE_PATH = "/api/admin/recordings";

    private final AdminCallRecordingMapper recordingMapper;
    private final RecordingStorageProperties recordingProperties;

    /**
     * 查询指定通话的全部录音元数据
     *
     * @param callId 业务通话主键 ID
     * @return 录音元数据列表 (按开始时间倒序)
     */
    public List<RecordingMetaVO> listByCallId(Long callId) {
        if (callId == null) {
            return List.of();
        }
        return findByCallId(callId).stream().map(this::toMeta).toList();
    }

    /**
     * 按通话主键定位最新一条可读取录音
     *
     * @param callId 业务通话主键 ID
     * @return 已解析的录音 (含文件路径与元数据)
     */
    public Optional<ResolvedRecording> resolveLatestByCallId(Long callId) {
        for (CallRecordingEntity entity : findByCallId(callId)) {
            Optional<ResolvedRecording> resolved = resolve(entity);
            if (resolved.isPresent()) {
                return resolved;
            }
        }
        return Optional.empty();
    }

    /**
     * 按录音业务唯一标识定位录音
     *
     * @param recordingId 录音业务唯一标识
     * @return 已解析的录音 (含文件路径与元数据)
     */
    public Optional<ResolvedRecording> resolveByRecordingId(String recordingId) {
        if (recordingId == null || recordingId.isBlank()) {
            return Optional.empty();
        }
        CallRecordingEntity entity = recordingMapper.selectOne(new LambdaQueryWrapper<CallRecordingEntity>()
                .eq(CallRecordingEntity::getRecordingId, recordingId.trim()));
        return entity == null ? Optional.empty() : resolve(entity);
    }

    /**
     * 查询指定通话的录音元数据实体
     */
    private List<CallRecordingEntity> findByCallId(Long callId) {
        return recordingMapper.selectList(new LambdaQueryWrapper<CallRecordingEntity>()
                .eq(CallRecordingEntity::getCallId, callId)
                .orderByDesc(CallRecordingEntity::getStartedAt)
                .orderByDesc(CallRecordingEntity::getId));
    }

    /**
     * 解析单条录音记录为可读取文件
     * <p>
     * 仅受理本地共享存储路径：远端对象存储地址不参与本地文件读取；
     * 解析结果越出共享根目录时一律拒绝，避免脏数据或路径篡改导致任意文件读取。
     * </p>
     */
    private Optional<ResolvedRecording> resolve(CallRecordingEntity entity) {
        Path file = RecordingPathLayout.resolveStoredPath(recordingProperties, entity.getObjectKey());
        if (file == null) {
            return Optional.empty();
        }
        if (!RecordingPathLayout.isInsideBaseDir(recordingProperties, file)) {
            log.warn("⚠️ [录音] 拒绝访问共享目录之外的录音路径: recordingId={}, path={}",
                    entity.getRecordingId(), file);
            return Optional.empty();
        }
        if (!Files.isRegularFile(file)) {
            log.debug("[录音] 文件尚未落盘或已被归档: recordingId={}, path={}", entity.getRecordingId(), file);
            return Optional.empty();
        }
        return Optional.of(new ResolvedRecording(file, toMeta(entity)));
    }

    /**
     * 实体转视图并填充访问地址
     */
    private RecordingMetaVO toMeta(CallRecordingEntity entity) {
        Path file = RecordingPathLayout.resolveStoredPath(recordingProperties, entity.getObjectKey());
        boolean localFile = file != null && RecordingPathLayout.isInsideBaseDir(recordingProperties, file);
        boolean available = localFile && Files.isRegularFile(file);

        String streamUrl;
        String downloadUrl;
        if (localFile) {
            // 本地共享存储统一走管理端流式代理，支持 HTTP Range 拖拽复播
            String suffix = entity.getRecordingId() != null && !entity.getRecordingId().isBlank()
                    ? "/by-rec-id/" + entity.getRecordingId()
                    : "/" + entity.getCallId();
            streamUrl = BASE_PATH + suffix + "/stream";
            downloadUrl = BASE_PATH + suffix + "/download";
        } else if (RecordingPathLayout.isRemoteUrl(entity.getObjectKey())) {
            // 远端对象存储地址可直接交给浏览器
            streamUrl = entity.getObjectKey();
            downloadUrl = entity.getObjectKey();
        } else {
            streamUrl = null;
            downloadUrl = null;
        }

        return RecordingMetaVO.builder()
                .recordingId(entity.getRecordingId())
                .callId(entity.getCallId())
                .legId(entity.getLegId())
                .nodeId(entity.getNodeId())
                .status(entity.getStatus())
                .storageType(entity.getStorageType())
                .objectKey(entity.getObjectKey())
                .mediaFormat(entity.getMediaFormat())
                .sizeBytes(entity.getSizeBytes())
                .durationMs(entity.getDurationMs())
                .startedAt(entity.getStartedAt())
                .completedAt(entity.getCompletedAt())
                .available(available)
                .streamUrl(streamUrl)
                .downloadUrl(downloadUrl)
                .build();
    }

    /**
     * 已解析的录音资源
     *
     * @param file 共享存储上的录音文件路径
     * @param meta 录音元数据 (含复播与下载地址)
     */
    public record ResolvedRecording(Path file, RecordingMetaVO meta) {
    }
}
