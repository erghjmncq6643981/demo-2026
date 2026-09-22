package com.chandler.fcc.admin.controller.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 管理员切换指定坐席接听终端的请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "管理员切换坐席接听终端请求")
public class SwitchAgentEndpointReq implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 坐席工号。 */
    @NotBlank(message = "坐席工号不能为空")
    @Schema(description = "被操作坐席工号", example = "901001")
    private String workNo;

    /** 接听终端类型。 */
    @NotBlank(message = "接听终端类型不能为空")
    @Schema(description = "接听终端类型，仅支持 WEBRTC 或 SIP", example = "SIP")
    private String endpointType;

    /** 目标终端值。 */
    @Schema(description = "已经通过话机拨号绑定的 SIP 分机", example = "1007")
    private String endpointValue;
}
