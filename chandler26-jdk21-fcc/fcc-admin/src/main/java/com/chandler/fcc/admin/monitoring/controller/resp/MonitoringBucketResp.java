package com.chandler.fcc.admin.monitoring.controller.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 完整事实按时间、方向和自动外呼归属聚合的紧凑桶，不携带单通话身份。 */
@Data
@Schema(description="话务监控时间桶")
public class MonitoringBucketResp {
    @Schema(description="北京时间桶起点，不是单通话开始时间",example="2026-10-04T09:00:00")
    private String initiatedAt;
    @Schema(description="呼叫方向：INBOUND、OUTBOUND 或 INTERNAL",example="OUTBOUND")
    private String direction;
    @Schema(description="是否关联自动外呼任务",example="true")
    private boolean automatic;
    @Schema(description="此桶完整通话数量",example="2500")
    private long total;
    @Schema(description="有应答事实的通话数量",example="1800")
    private long connected;
    @Schema(description="此桶通话时长总和，单位毫秒",example="60000")
    private long talkDurationMs;
}
