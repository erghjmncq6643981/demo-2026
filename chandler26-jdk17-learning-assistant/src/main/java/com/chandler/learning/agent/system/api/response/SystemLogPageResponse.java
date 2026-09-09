package com.chandler.learning.agent.system.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/** 系统日志分页响应。 */
@Data
public class SystemLogPageResponse {

    /** 当前页日志。 */
    @Schema(description = "当前页系统日志")
    private List<SystemLogResponse> items;

    /** 符合条件的日志总数。 */
    @Schema(description = "日志总数")
    private long total;

    /** 当前页码，从 1 开始。 */
    @Schema(description = "当前页码")
    private int page;

    /** 每页数量。 */
    @Schema(description = "每页数量")
    private int pageSize;
}
