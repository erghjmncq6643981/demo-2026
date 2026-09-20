package com.chandler.fcc.admin.flow.controller.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * 发布 IVR 流程草稿的请求对象。
 */
@Getter
@Setter
@Schema(description = "发布 IVR 流程草稿请求")
public class PublishFlowReq {

    @NotBlank(message = "待发布版本不能为空")
    @Schema(description = "从版本列表取得的待发布版本号", example = "v1.1.0")
    private String version;

    @Schema(description = "本次发布的中文业务备注", example = "调整售后按键路由")
    private String remark;
}
