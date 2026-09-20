package com.chandler.fcc.admin.flow.controller.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

/**
 * IVR 流程发布后的数据库与运行端状态。
 */
@Getter
@Builder
@Schema(description = "IVR 流程发布结果")
public class FlowPublishResp {

    @Schema(description = "已发布版本号", example = "v1.1.0")
    private String version;

    @Schema(description = "数据库版本状态", example = "PUBLISHED")
    private String publishStatus;

    @Schema(description = "运行端激活状态；发布返回时通常仍待确认", example = "PENDING")
    private String runtimeActivationStatus;
}
