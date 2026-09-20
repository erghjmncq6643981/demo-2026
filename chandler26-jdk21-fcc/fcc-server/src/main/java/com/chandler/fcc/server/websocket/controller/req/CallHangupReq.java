package com.chandler.fcc.server.websocket.controller.req;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 指定业务通话的挂机请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "指定业务通话的挂机请求")
public class CallHangupReq {

    @Schema(description = "坐席工号，必须与登录主体一致")
    private String workNo;

    @Schema(description = "业务通话标识，不是话道 UUID")
    private String callId;

    @Schema(description = "请求挂机原因")
    private String reason;
}
