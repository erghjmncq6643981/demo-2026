package com.chandler.fcc.admin.flow.controller.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

/**
 * IVR 画布使用的公共流程动作目录项。
 */
@Getter
@Builder
@Schema(description = "流程动作目录项")
public class FlowActionResp {

    @Schema(description = "稳定业务动作代码", example = "READ_DTMF")
    private String code;

    @Schema(description = "动作中文名称", example = "播放提示并收取按键")
    private String label;

    @Schema(description = "执行器类型代码", example = "FNODE_COMMAND")
    private String executorType;

    @Schema(description = "执行器类型中文名称", example = "FNode 指令")
    private String executorTypeLabel;

    @Schema(description = "稳定操作代码或规范 FNode 方法", example = "FNode.ReadDTMF")
    private String operation;
}
