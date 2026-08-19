package com.chandler.learning.agent.domain.dto.learning;

import lombok.Data;

import java.util.List;

/** 用户中心分页结果。 */
@Data
public class AdminUserPageResponse {

    private List<AdminUserResponse> items;
    private long total;
    private int page;
    private int pageSize;
}
