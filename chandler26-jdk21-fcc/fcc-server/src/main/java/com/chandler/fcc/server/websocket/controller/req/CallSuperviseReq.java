package com.chandler.fcc.server.websocket.controller.req;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 班长干预请求，当前版本只用于明确返回能力未开放。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "班长干预请求")
public class CallSuperviseReq {

    @Schema(description = "请求操作的班长工号")
    private String supervisorWorkNo;

    @Schema(description = "目标坐席工号")
    private String targetWorkNo;

    @Schema(description = "干预类型，可选 SPY、COACH、BARGE 或 KILL")
    private String type;

    @Schema(description = "业务通话标识，不是话道 UUID")
    private String callId;
}
