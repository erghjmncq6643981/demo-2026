package com.chandler.fcc.admin.flow.controller.req;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 创建呼入流程的请求参数，流程版本内容由后续草稿保存接口维护。
 */
@Data
@Schema(description = "创建呼入流程请求")
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
}
