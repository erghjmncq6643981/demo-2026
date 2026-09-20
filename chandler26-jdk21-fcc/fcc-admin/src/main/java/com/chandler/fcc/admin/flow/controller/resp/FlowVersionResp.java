package com.chandler.fcc.admin.flow.controller.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

/**
 * IVR 流程版本摘要或按需加载的版本详情。
 */
@Getter
@Builder
@Schema(description = "IVR 流程版本")
public class FlowVersionResp {

    @Schema(description = "版本数据库标识，按不透明字符串传输", example = "820000000000000021")
    private String id;

    @Schema(description = "公开版本号", example = "v1.0.0")
    private String version;

    @Schema(description = "流程内部递增版本序号", example = "1")
    private Integer versionNo;

    @Schema(description = "发布状态：DRAFT、PUBLISHED 或 ARCHIVED", example = "DRAFT")
    private String publishStatus;

    @Schema(description = "完整流程定义 JSON；仅版本详情接口返回")
    private String definitionJson;

    @Schema(description = "正式发布时间；草稿为空")
    private LocalDateTime publishedAt;

    @Schema(description = "版本创建人账号", example = "admin")
    private String createdBy;

    @Schema(description = "版本创建时间")
    private LocalDateTime createdAt;
}
