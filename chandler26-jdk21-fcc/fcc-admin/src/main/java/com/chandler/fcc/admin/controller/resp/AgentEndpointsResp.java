package com.chandler.fcc.admin.controller.resp;

import com.chandler.fcc.admin.agent.application.model.AgentEndpointOverview;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 坐席终端绑定与当前选择响应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "坐席终端绑定与当前选择")
public class AgentEndpointsResp implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 坐席工号。 */
    @Schema(description = "坐席工号", example = "901001")
    private String workNo;

    /** 坐席姓名。 */
    @Schema(description = "坐席姓名", example = "张三")
    private String agentName;

    /** 当前接听终端类型。 */
    @Schema(description = "当前接听终端类型", example = "WEBRTC")
    private String activeEndpointType;

    /** 当前接听终端值。 */
    @Schema(description = "当前接听终端值", example = "901001")
    private String activeEndpointValue;

    /** WebRTC 工号分机。 */
    @Schema(description = "WebRTC 工号分机", example = "901001")
    private String webrtcWorkNo;

    /** 已验证的 SIP 分机。 */
    @Schema(description = "已通过话机拨号流程绑定的 SIP 分机", example = "1007")
    private String sipExtension;

    /** 坐席资料手机号。 */
    @Schema(description = "坐席资料中的手机号，不代表手机接听已实现", example = "13800138000")
    private String mobilePhone;

    /** 可切换的 SIP 分机。 */
    @Schema(description = "可以切换的已验证 SIP 绑定")
    private List<String> availableSipExtensions;

    /**
     * 将应用查询结果转换为接口响应。
     *
     * @param overview 应用查询结果
     * @return 接口响应
     */
    public static AgentEndpointsResp from(AgentEndpointOverview overview) {
        return AgentEndpointsResp.builder()
            .workNo(overview.getWorkNo())
            .agentName(overview.getAgentName())
            .activeEndpointType(overview.getActiveEndpointType())
            .activeEndpointValue(overview.getActiveEndpointValue())
            .webrtcWorkNo(overview.getWebrtcWorkNo())
            .sipExtension(overview.getSipExtension())
            .mobilePhone(overview.getMobilePhone())
            .availableSipExtensions(overview.getAvailableSipExtensions())
            .build();
    }
}
