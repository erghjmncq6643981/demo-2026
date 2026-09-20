package com.chandler.fcc.admin.controller.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 将呼入 DID 被叫号码绑定到业务流程的请求。
 */
@Getter
@Setter
@Schema(description = "呼入DID号码与IVR流程绑定请求")
public class BindDidFlowReq {

    /**
     * 稳定流程代码。
     */
    @NotBlank(message = "流程代码不能为空")
    @Size(max = 64, message = "流程代码长度不能超过 64")
    @Schema(description = "DID来电进入的业务流程代码", example = "SERVICE_INBOUND")
    private String flowKey;
}
