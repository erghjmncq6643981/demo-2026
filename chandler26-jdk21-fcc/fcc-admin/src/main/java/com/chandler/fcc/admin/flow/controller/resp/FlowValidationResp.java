package com.chandler.fcc.admin.flow.controller.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * IVR 流程定义校验结果。
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "IVR 流程定义校验结果")
public class FlowValidationResp {

    @Schema(description = "定义是否通过当前运行时契约校验", example = "true")
    private Boolean valid;

    @Schema(description = "服务端规范化后的完整定义 JSON")
    private String normalizedDefinitionJson;
}
