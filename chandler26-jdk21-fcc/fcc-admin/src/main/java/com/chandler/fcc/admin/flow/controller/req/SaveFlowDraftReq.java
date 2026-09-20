package com.chandler.fcc.admin.flow.controller.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * 保存 IVR 流程草稿的请求对象。
 */
@Getter
@Setter
@Schema(description = "保存 IVR 流程草稿请求")
public class SaveFlowDraftReq {

    @NotBlank(message = "流程定义不能为空")
    @Schema(description = "完整流程模型 JSON；服务端将严格校验节点和可编辑参数", example = "{\"routeMode\":\"IVR\"}")
    private String definitionJson;
}
