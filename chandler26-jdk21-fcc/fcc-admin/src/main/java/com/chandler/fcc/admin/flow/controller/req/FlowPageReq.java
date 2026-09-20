package com.chandler.fcc.admin.flow.controller.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

/**
 * IVR 流程和版本摘要列表的分页请求。
 */
@Getter
@Setter
@Schema(description = "IVR 流程分页请求")
public class FlowPageReq {

    @Min(value = 1, message = "页码必须大于 0")
    @Schema(description = "页码，从 1 开始", example = "1")
    private long pageNum = 1;

    @Min(value = 1, message = "每页数量必须大于 0")
    @Max(value = 100, message = "每页数量不能超过 100")
    @Schema(description = "每页摘要数量，最大 100", example = "20")
    private long pageSize = 20;
}
