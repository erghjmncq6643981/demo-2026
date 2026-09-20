package com.chandler.fcc.admin.flow.controller.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

/**
 * 单通话执行过程的分页响应，只包含数据库中存在的版本快照和执行事实。
 */
@Data
@Schema(description = "通话流程执行记录响应")
public class FlowExecutionResp {

    @Schema(description = "通话实例和模型版本快照，没有实例时为空对象")
    private FlowExecutionInstanceResp instance;

    @Schema(description = "按执行记录顺序返回的动作事实，每次尝试独立保留")
    private List<FlowExecutionStepResp> steps;

    @Schema(description = "下一页游标，为空表示没有更多记录；标识按字符串传输")
    private String nextCursor;
}
