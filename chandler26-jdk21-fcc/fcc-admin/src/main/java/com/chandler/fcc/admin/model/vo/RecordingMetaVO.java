package com.chandler.fcc.admin.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 通话录音元数据与访问地址视图对象 VO
 * <p>
 * 录音地址（{@link #objectKey}）即下发录音指令时声明、由 FreeSWITCH 写入的共享存储绝对路径；
 * 库内地址与磁盘文件一一对应。前端通过 {@link #streamUrl} 复播、通过 {@link #downloadUrl} 下载，
 * 避免把宿主机文件路径直接暴露给浏览器。
 * </p>
 *
 * @author Chandler
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "通话录音元数据与访问地址")
public class RecordingMetaVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "录音业务唯一标识 (recording_id)")
    private String recordingId;

    @Schema(description = "关联业务通话主键 ID")
    private Long callId;

    @Schema(description = "关联话道 Leg ID")
    private Long legId;

    @Schema(description = "写出录音文件的 Sidecar/FreeSWITCH 节点")
    private String nodeId;

    @Schema(description = "录音状态 (RECORDING / COMPLETED / FAILED)")
    private String status;

    @Schema(description = "存储类型 (LOCAL 共享存储 / OBJECT_STORAGE 对象存储)")
    private String storageType;

    @Schema(description = "录音文件地址 (共享存储绝对路径或远端 URL)")
    private String objectKey;

    @Schema(description = "媒体封装格式 (wav / mp3)")
    private String mediaFormat;

    @Schema(description = "文件大小（字节）")
    private Long sizeBytes;

    @Schema(description = "录音时长（毫秒）")
    private Long durationMs;

    @Schema(description = "录音开始时间")
    private LocalDateTime startedAt;

    @Schema(description = "录音完成时间")
    private LocalDateTime completedAt;

    @Schema(description = "文件当前是否可读取")
    private Boolean available;

    @Schema(description = "浏览器复播地址 (支持 HTTP Range 断点拖拽)")
    private String streamUrl;

    @Schema(description = "浏览器下载地址 (Content-Disposition: attachment)")
    private String downloadUrl;
}
