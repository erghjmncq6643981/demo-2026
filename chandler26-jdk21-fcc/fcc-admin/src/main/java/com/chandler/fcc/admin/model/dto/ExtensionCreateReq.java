package com.chandler.fcc.admin.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 通信分机创建请求 DTO
 * <p>
 * 创建分机时将通过 SidecarAdminClient 同步写入 FreeSWITCH directory 配置并执行 reloadxml。
 * </p>
 *
 * @author Chandler
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "分机创建请求参数")
public class ExtensionCreateReq implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "分机号不能为空")
    @Schema(description = "分机号码 (数字如 1001)", example = "1001")
    private String extension;

    @NotBlank(message = "分机注册密码不能为空")
    @Schema(description = "SIP 注册密码", example = "1234")
    private String password;

    @Schema(description = "终端类型 (SIP, WEBRTC)", example = "SIP")
    @Builder.Default
    private String endpointType = "SIP";
}
