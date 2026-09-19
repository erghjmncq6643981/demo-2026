package com.chandler.fcc.admin.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 客户端版本发布记录视图对象 VO
 *
 * @author Chandler
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "客户端版本发布展示视图")
public class ClientVersionReleaseVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "版本记录主键 ID", example = "201")
    private Long id;

    @Schema(description = "版本号", example = "1.2.0")
    private String version;

    @Schema(description = "平台 (WINDOWS, MAC, LINUX, WEB)", example = "WINDOWS")
    private String platform;

    @Schema(description = "下载地址", example = "https://cdn.example.com/client/v1.2.0/setup.exe")
    private String downloadUrl;

    @Schema(description = "MD5 校验和", example = "e10adc3949ba59abbe56e057f20f883e")
    private String fileMd5;

    @Schema(description = "是否强制升级", example = "false")
    private Boolean forceUpdate;

    @Schema(description = "发布状态 (DRAFT, RELEASED, DEPRECATED)", example = "RELEASED")
    private String status;

    @Schema(description = "更新日志说明")
    private String releaseNotes;

    @Schema(description = "正式发布时间")
    private LocalDateTime releasedAt;

    @Schema(description = "创建发布人", example = "admin")
    private String createdBy;
}
