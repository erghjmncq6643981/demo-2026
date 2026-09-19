package com.chandler.fcc.admin.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 坐席接听方式/终端快速切换入参 DTO
 * <p>
 * 支持三种接听方式：
 * 1. WEBRTC: 使用坐席工号注册 FreeSWITCH WebRTC 软电话 (如 901001)
 * 2. SIP: 绑定工位硬件桌面电话机 (如 1007, 1008, 1017)
 * 3. MOBILE: 绑定随行移动手机 (如 13800000001)
 * </p>
 *
 * @author Chandler
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "坐席接听方式与终端切换请求参数")
public class SwitchEndpointReq implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "坐席工号不能为空")
    @Schema(description = "坐席工号", example = "901001")
    private String workNo;

    @NotBlank(message = "接听终端类型不能为空")
    @Schema(description = "终端类型 (WEBRTC, SIP, MOBILE)", example = "WEBRTC")
    private String endpointType;

    @Schema(description = "指定终端标识值 (SIP分机号如1007，或手机号如138xxxx)", example = "1007")
    private String endpointValue;
}
