package com.chandler.fcc.server.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

/**
 * 通话录音元数据事实持久化实体 (fcc_call_recording)
 * <p>
 * 对应 MySQL 中 fcc_call_recording 表，维护通话双向混音及单通道录音文件的元数据、存储路径及生命周期。
 * </p>
 *
 * @author Chandler
 */
@TableName("fcc_call_recording")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
@SuperBuilder
public class CallRecordingEntity extends BaseEntity {

    /**
     * 录音业务唯一标识 (recording_id)
     */
    private String recordingId;

    /**
     * 关联业务通话 ID
     */
    private Long callId;

    /**
     * 关联话道 Leg ID
     */
    private Long legId;

    /**
     * 关联媒体桥接 ID
     */
    private Long bridgeId;

    /**
     * 写出该录音文件的 Sidecar/FreeSWITCH 节点标识
     */
    private String nodeId;

    /**
     * 录音状态 (RECORDING, COMPLETED, FAILED)
     */
    private String status;

    /**
     * 存储类型 (LOCAL, OBJECT_STORAGE, S3, MINIO)
     */
    private String storageType;

    /**
     * 存储桶名称
     */
    private String bucketName;

    /**
     * 对象存储 Key 或本地存储路径
     */
    private String objectKey;

    /**
     * 媒体封装格式 (wav, mp3)
     */
    private String mediaFormat;

    /**
     * 文件大小（字节）
     */
    private Long sizeBytes;

    /**
     * 录音时长（毫秒）
     */
    private Long durationMs;

    /**
     * 文件 SHA-256 校验和
     */
    private String checksum;

    /**
     * 录音开始时间 (UTC)
     */
    private LocalDateTime startedAt;

    /**
     * 录音完成时间 (UTC)
     */
    private LocalDateTime completedAt;

    /**
     * 归档保留截止时间 (UTC)
     */
    private LocalDateTime retainUntil;
}
