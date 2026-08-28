package com.chandler.learning.agent.ai.chat.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Data;

import java.util.List;

/** AI 会话分页结果。 */
@Data
public class AdminAiSessionPageResponse {

    @Schema(description = "总数量")
    private Long total;
    @Schema(description = "页码")
    private Integer page;
    @Schema(description = "每页数量")
    private Integer pageSize;
    @Schema(description = "列表数据")
    private List<AdminAiSessionResponse> items;
}
