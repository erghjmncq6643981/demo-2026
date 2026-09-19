package com.chandler.fcc.server.recording;

import com.chandler.fcc.common.recording.RecordingPathLayout;
import com.chandler.fcc.common.recording.RecordingStorageProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;

/**
 * 录音落盘路径解析器
 * <p>
 * 在<b>下发录音指令之前</b>确定录音文件地址：该地址是一个共享存储的绝对路径，
 * FreeSWITCH 的 {@code uuid_record} 直接写入同一路径，控制面据此落库，
 * 管理端再从同一路径读取对外复播与下载。因此它天然是三端一致的事实来源。
 * </p>
 *
 * @author Chandler
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecordingPathResolver {

    private final RecordingStorageProperties properties;

    /**
     * 解析一次录音的目标地址
     * <p>
     * 会顺带确保日期分层子目录存在——FreeSWITCH 的 {@code uuid_record} 只创建文件不创建目录，
     * 若目录缺失会导致录音静默失败。
     * </p>
     *
     * @param callId           业务通话标识 (作为文件名，全通话周期稳定)
     * @param startedAt        录音开始时间 (决定日期分层子目录)
     * @param explicitPath     流程节点中显式声明的路径，非空时优先采用
     * @return 录音目标地址描述
     */
    public RecordingTarget resolve(String callId, LocalDateTime startedAt, String explicitPath) {
        LocalDateTime at = startedAt != null ? startedAt : LocalDateTime.now();

        String absolutePath = (explicitPath != null && !explicitPath.isBlank())
                ? explicitPath.trim()
                : RecordingPathLayout.buildAbsolutePath(properties, callId, at);

        if (absolutePath == null) {
            throw new IllegalArgumentException("无法确定录音文件地址: callId 为空");
        }

        String recordingId = RecordingPathLayout.recordingIdOf(callId);
        String format = extractFormat(absolutePath);

        ensureParentDirectory(Paths.get(absolutePath));

        return new RecordingTarget(recordingId, absolutePath, format, at);
    }

    /**
     * 确保录音文件所在目录存在
     *
     * @param targetFile 录音文件绝对路径
     */
    private void ensureParentDirectory(Path targetFile) {
        Path parent = targetFile.getParent();
        if (parent == null) {
            return;
        }
        try {
            Files.createDirectories(parent);
        } catch (IOException e) {
            // 目录可能由 FreeSWITCH 侧先行创建，或调用方无写权限；此处仅告警不阻断指令下发
            log.warn("⚠️ [录音] 共享目录创建失败 (若 FreeSWITCH 侧已挂载同一路径可忽略): path={}, err={}",
                    parent, e.getMessage());
        }
    }

    /**
     * 从路径推断封装格式
     *
     * @param path 录音文件路径
     * @return 小写扩展名；无法识别时返回 wav
     */
    private String extractFormat(String path) {
        int dot = path.lastIndexOf('.');
        if (dot < 0 || dot == path.length() - 1) {
            return "wav";
        }
        return path.substring(dot + 1).toLowerCase();
    }

    /**
     * 录音目标地址描述
     *
     * @param recordingId  录音业务唯一标识 (fcc_call_recording.recording_id)
     * @param absolutePath 共享存储绝对路径 (下发 FreeSWITCH 并落库的 object_key)
     * @param mediaFormat  封装格式
     * @param startedAt    录音开始时间
     */
    public record RecordingTarget(String recordingId, String absolutePath, String mediaFormat,
                                  LocalDateTime startedAt) {
    }
}
