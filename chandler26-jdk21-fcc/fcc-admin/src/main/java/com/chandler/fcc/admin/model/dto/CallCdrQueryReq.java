package com.chandler.fcc.admin.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 通话历史话单 CDR 多条件分页查询请求 DTO
 *
 * @author Chandler
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "通话话单检索请求参数")
public class CallCdrQueryReq implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "号码关键字 (主叫或被叫模糊匹配)", example = "13800")
    private String number;

    @Schema(description = "主叫号码 (支持前缀模糊)", example = "13800")
    private String caller;

    @Schema(description = "被叫号码", example = "1001")
    private String callee;

    @Schema(description = "服务坐席工号", example = "901001")
    private String agentWorkNo;

    @Schema(description = "服务坐席姓名 (模糊匹配，同时兼顾 fcc_agent 中的在册姓名)", example = "张三")
    private String agentName;

    @Schema(description = "通话ID / 控制标识 ctrl_id (模糊匹配)", example = "fcc-inbound")
    private String ctrlId;

    @Schema(description = "呼叫方向 (INBOUND, OUTBOUND, INTERNAL)", example = "INBOUND")
    private String direction;

    @Schema(description = "通话终态状态 (BRIDGED, ANSWERED, COMPLETED, NO_ANSWER, BUSY)", example = "COMPLETED")
    private String status;

    @Schema(description = "挂机原因 (NORMAL_CLEARING, USER_BUSY, NO_ANSWER, ORIGINATOR_CANCEL)", example = "NORMAL_CLEARING")
    private String hangupCause;

    @Schema(description = "查询起始时间 (initiatedAt >= startTime)")
    private LocalDateTime startTime;

    @Schema(description = "查询结束时间 (initiatedAt <= endTime)")
    private LocalDateTime endTime;

    @Schema(description = "页码 (默认 1)", example = "1")
    @Builder.Default
    private long pageNum = 1;

    @Schema(description = "每页记录数 (默认 20)", example = "20")
    @Builder.Default
    private long pageSize = 20;
}
