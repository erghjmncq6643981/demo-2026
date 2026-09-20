package com.chandler.fcc.server.websocket.controller.req;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 坐席人工外呼请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "坐席人工外呼请求")
public class CallOutboundReq {

    @Schema(description = "坐席工号，必须与登录主体一致")
    private String workNo;

    @Schema(description = "申请使用的外呼主叫号码")
    private String callerPhone;

    @Schema(description = "外呼被叫号码")
    private String calleePhone;

    @Schema(description = "坐席录入的客户姓名")
    private String customerName;

    @Schema(description = "坐席录入的客户单位")
    private String companyName;
}
