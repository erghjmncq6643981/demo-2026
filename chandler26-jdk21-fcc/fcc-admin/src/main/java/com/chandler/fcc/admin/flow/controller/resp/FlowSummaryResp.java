package com.chandler.fcc.admin.flow.controller.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

/**
 * 业务流程列表中的轻量摘要。
 */
@Getter
@Builder
@Schema(description = "业务流程摘要")
public class FlowSummaryResp {

    @Schema(description = "流程数据库标识，按不透明字符串传输", example = "820000000000000001")
    private String id;

    @Schema(description = "稳定流程代码", example = "SERVICE_INBOUND")
    private String flowKey;

    @Schema(description = "流程中文名称", example = "客户服务热线")
    private String flowName;

    @Schema(description = "流程模型类型", example = "INBOUND")
    private String modelType;

    @Schema(description = "流程状态", example = "PUBLISHED")
    private String status;

    @Schema(description = "当前已发布版本；尚未发布时为空", example = "v1.0.0")
    private String currentVersion;

    @Schema(description = "是否为随服务部署且不可编辑的系统模型", example = "true")
    private boolean system;
}
