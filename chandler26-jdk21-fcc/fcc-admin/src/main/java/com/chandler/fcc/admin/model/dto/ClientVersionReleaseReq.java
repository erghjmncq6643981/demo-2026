package com.chandler.fcc.admin.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 客户端新版本发布请求 DTO
 *
 * @author Chandler
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "客户端版本发布请求参数")
public class ClientVersionReleaseReq implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "版本号不能为空")
    @Schema(description = "版本号 (如 1.2.0)", example = "1.2.0")
    private String version;

    @Schema(description = "目标平台 (WINDOWS, MAC, LINUX, WEB)", example = "WINDOWS")
    @Builder.Default
    private String platform = "WINDOWS";

    @NotBlank(message = "下载地址不能为空")
    @Schema(description = "软件包下载 URL", example = "https://cdn.example.com/client/v1.2.0/setup.exe")
    private String downloadUrl;

    @Schema(description = "软件包 MD5 校验码", example = "e10adc3949ba59abbe56e057f20f883e")
    private String fileMd5;

    @Schema(description = "是否强制全员升级", example = "false")
    @Builder.Default
    private Boolean forceUpdate = false;

    @Schema(description = "版本升级说明", example = "优化SIP软电话接通时延，修复偶发单通问题")
    private String releaseNotes;
}
