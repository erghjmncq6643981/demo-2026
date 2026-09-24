package com.chandler.fcc.admin.flow.controller.req;

import com.chandler.fcc.common.enums.FlowTemplateType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 创建可维护业务流程的请求参数，流程版本内容由后续草稿保存接口维护。
 */
@Data
@Schema(description = "创建业务流程请求")
public class CreateFlowReq {

    /**
     * 流程的唯一业务代码。
     */
    @Schema(description = "流程代码，以字母开头，仅允许字母、数字、下划线和连字符，最长64位")
    private String flowKey;

    /**
     * 管理页面展示的流程名称。
     */
    @Schema(description = "流程业务名称，不能为空，最长128个字符")
    private String flowName;

    /**
     * 创建后不可变的流程业务类型。
     */
    @NotNull(message = "必须选择流程类型")
    @Schema(description = "流程业务类型；当前仅支持呼入 IVR", example = "INBOUND")
    private FlowTemplateType modelType;
}
