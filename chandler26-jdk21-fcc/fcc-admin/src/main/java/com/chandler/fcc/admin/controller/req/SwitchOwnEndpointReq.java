package com.chandler.fcc.admin.controller.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 当前登录坐席切换本人接听终端的请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "当前坐席接听终端切换请求")
public class SwitchOwnEndpointReq implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 接听终端类型。 */
    @NotBlank(message = "接听终端类型不能为空")
    @Schema(description = "接听终端类型，仅支持 WEBRTC 或 SIP", example = "WEBRTC")
    private String endpointType;

    /** 目标终端值。 */
    @Schema(description = "已绑定 SIP 分机；选择 WEBRTC 时可以不传", example = "1007")
    private String endpointValue;
}
