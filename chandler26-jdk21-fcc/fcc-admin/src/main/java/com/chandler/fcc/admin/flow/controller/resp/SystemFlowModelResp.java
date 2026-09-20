package com.chandler.fcc.admin.flow.controller.resp;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

/**
 * 随应用发布的固定通话模型详情。
 */
@Getter
@Builder
@Schema(description = "固定通话模型详情")
public class SystemFlowModelResp {

    @Schema(description = "固定模板代码", example = "INBOUND")
    private String template;

    @Schema(description = "包含节点、动作、执行器和迁移条件的完整模型")
    private JsonNode definition;
}
