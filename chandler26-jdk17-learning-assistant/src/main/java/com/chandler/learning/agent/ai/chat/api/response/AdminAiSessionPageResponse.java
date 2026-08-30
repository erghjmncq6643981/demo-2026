package com.chandler.learning.agent.ai.chat.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Data;

import java.util.List;

/** AI 会话分页结果。 */
@Data
public class AdminAiSessionPageResponse {

    @Schema(description = "分页数据总数")
    private Long total;
    @Schema(description = "页码")
    private Integer page;
    @Schema(description = "每页数量")
    private Integer pageSize;
    @Schema(description = "分页数据列表")
    private List<AdminAiSessionResponse> items;
}
