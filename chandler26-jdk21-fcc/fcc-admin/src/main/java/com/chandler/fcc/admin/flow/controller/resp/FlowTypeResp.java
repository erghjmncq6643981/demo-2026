package com.chandler.fcc.admin.flow.controller.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

/**
 * 管理端允许创建的流程业务类型目录项。
 */
@Getter
@Builder
@Schema(description = "可创建流程业务类型")
public class FlowTypeResp {

    @Schema(description = "稳定类型代码，创建后不可修改", example = "INBOUND")
    private String code;

    @Schema(description = "类型中文名称", example = "呼入 IVR")
    private String label;

    @Schema(description = "该类型覆盖的业务闭环", example = "DID 呼入、导航收号、条件分支、坐席路由和服务评价")
    private String description;
}
