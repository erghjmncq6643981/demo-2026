package com.chandler.fcc.admin.agent.application.model;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

/**
 * 坐席终端绑定和当前选择的应用层查询结果。
 */
@Getter
@Builder
public class AgentEndpointOverview {

    /** 坐席工号。 */
    private String workNo;

    /** 坐席姓名。 */
    private String agentName;

    /** 当前接听终端类型。 */
    private String activeEndpointType;

    /** 当前接听终端值。 */
    private String activeEndpointValue;

    /** WebRTC 工号分机。 */
    private String webrtcWorkNo;

    /** 已通过话机拨号流程绑定的 SIP 分机。 */
    private String sipExtension;

    /** 仅作为资料保存的手机号。 */
    private String mobilePhone;

    /** 可以切换的已验证 SIP 绑定。 */
    private List<String> availableSipExtensions;
}
