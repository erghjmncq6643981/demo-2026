package com.chandler.learning.agent.ai.chat.api;

import lombok.Data;

import java.util.List;

/** AI 会话分页结果。 */
@Data
public class AdminAiSessionPageResponse {

    private Long total;
    private Integer page;
    private Integer pageSize;
    private List<AdminAiSessionResponse> items;
}
