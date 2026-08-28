package com.chandler.learning.agent.identity.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Data;

import java.util.List;

/** 用户中心分页结果。 */
@Data
public class AdminUserPageResponse {

    @Schema(description = "列表数据")
    private List<AdminUserResponse> items;
    @Schema(description = "总数量")
    private long total;
    @Schema(description = "页码")
    private int page;
    @Schema(description = "每页数量")
    private int pageSize;
}
