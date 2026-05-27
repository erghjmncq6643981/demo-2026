package com.chandler.motivation.domain.dto.log;

import java.util.Collections;
import java.util.List;
import lombok.Data;

/**
 * 宝贝成长日志和操作日志的分页响应。
 */
@Data
public class ActivityLogPageResponse {
    private String category;
    private int pageNo;
    private int pageSize;
    private long total;
    private boolean hasMore;
    private List<ChildActivityLogResponse> records = Collections.emptyList();
}
