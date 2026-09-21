package com.chandler.fcc.common.dto.flow;

import com.chandler.fcc.common.protocol.ThirdPartyFlowProtocol;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * FCC 调用第三方流程服务时发送的固定协议请求。
 *
 * <p>第三方服务只接收业务上下文和动作输入，不接收内部 Java 类名、URL 或
 * FreeSWITCH 节点信息。{@code commandId} 是幂等边界，第三方必须原样回传。</p>
 */
@Schema(description = "FCC 第三方流程动作请求协议")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThirdPartyFlowRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 协议版本。
     */
    @JsonProperty("protocol_version")
    @Schema(description = "第三方流程协议版本，当前为 1.0", example = "1.0")
    @Builder.Default
    private String protocolVersion = ThirdPartyFlowProtocol.VERSION;

    /**
     * 本次动作的稳定幂等命令标识。
     */
    @JsonProperty("command_id")
    @Schema(description = "稳定幂等命令标识，第三方响应必须原样返回", example = "cmd-0199a1b2c3d4")
    private String commandId;

    /**
     * FCC 业务通话标识。
     */
    @JsonProperty("call_id")
    @Schema(description = "FCC 业务通话标识，第三方不得将其解释为话道 UUID", example = "820000000000000001")
    private String callId;

    /**
     * 固定流程实例标识。
     */
    @JsonProperty("flow_instance_id")
    @Schema(description = "固定流程实例标识，可为空", example = "820000000000000021")
    private String flowInstanceId;

    /**
     * 公共动作编码。
     */
    @Schema(description = "公共 FlowActionType 动作编码", example = "INVOKE_CONFIGURED_THIRD_PARTY")
    private String action;

    /**
     * 由业务服务提供的动作输入。
     */
    @Schema(description = "第三方动作所需的业务输入对象")
    private Object input;
}
