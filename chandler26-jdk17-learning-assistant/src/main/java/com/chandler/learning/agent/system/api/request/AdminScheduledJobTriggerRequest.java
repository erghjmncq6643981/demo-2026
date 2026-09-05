package com.chandler.learning.agent.system.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 后台定时任务手动触发请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminScheduledJobTriggerRequest {

    @Schema(description = "是否异步后台执行，默认 true；为 false 时将同步等待执行完成返回结果")
    private Boolean async = true;
}
