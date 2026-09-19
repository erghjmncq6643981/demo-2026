package com.chandler.fcc.admin.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 坐席三种接听方式与终端配置详情 VO
 *
 * @author Chandler
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "坐席三种接听终端配置全貌")
public class AgentEndpointsVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "坐席工号", example = "901001")
    private String workNo;

    @Schema(description = "坐席真实姓名", example = "钱丁君")
    private String agentName;

    @Schema(description = "当前激活生效的接听方式 (WEBRTC, SIP, MOBILE)", example = "WEBRTC")
    private String activeEndpointType;

    @Schema(description = "当前生效的通信标识 (软话机工号 / 硬件分机 / 手机号)", example = "901001")
    private String activeEndpointValue;

    @Schema(description = "软话机 (WebRTC) 对应工号", example = "901001")
    private String webrtcWorkNo;

    @Schema(description = "绑定的工位硬件桌面 SIP 分机号", example = "1007")
    private String sipExtension;

    @Schema(description = "绑定的随行移动手机号", example = "13800000001")
    private String mobilePhone;

    @Schema(description = "系统当前已开通可选的工位 SIP 硬件分机列表", example = "[\"1007\", \"1008\", \"1017\"]")
    private List<String> availableSipExtensions;
}
