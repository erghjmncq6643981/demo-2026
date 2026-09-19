package com.chandler.fcc.admin.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 客户端版本发布与自动升级管理实体 (fcc_client_version_release)
 * <p>
 * 对应 MySQL 中 fcc_client_version_release 表，记录 PC/Web 客户端版本发布包、MD5 与强更标记。
 * </p>
 *
 * @author Chandler
 */
@TableName("fcc_client_version_release")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class ClientVersionReleaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 版本发布雪花主键 ID
     */
    @TableId
    private Long id;

    /**
     * 租户 ID
     */
    private Long tenantId;

    /**
     * 客户端版本号 (如 1.2.0)
     */
    private String version;

    /**
     * 目标操作系统平台 (WINDOWS, MAC, LINUX, WEB)
     */
    private String platform;

    /**
     * 客户端安装包/更新包下载 URL
     */
    private String downloadUrl;

    /**
     * 软件包文件 MD5 校验和
     */
    private String fileMd5;

    /**
     * 是否强制升级 (true/false)
     */
    private Boolean forceUpdate;

    /**
     * 发布状态 (DRAFT, RELEASED, DEPRECATED)
     */
    private String status;

    /**
     * 版本更新日志与特性说明
     */
    private String releaseNotes;

    /**
     * 正式发布时间
     */
    private LocalDateTime releasedAt;

    /**
     * 发布操作人
     */
    private String createdBy;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
