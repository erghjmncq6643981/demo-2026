package com.chandler.fcc.admin.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 未接待漏话回拨待办视图对象 VO
 *
 * @author Chandler
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "漏话回拨待办视图")
public class CallbackTaskVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "任务 ID", example = "1001")
    private String id;

    @Schema(description = "客户手机号", example = "13988776655")
    private String phone;

    @Schema(description = "进线漏话时间", example = "2026-09-18 15:42:10")
    private String time;

    @Schema(description = "排队放弃原因", example = "坐席忙未接起放弃")
    private String reason;

    @Schema(description = "等待时长描述 (如 45秒, 1分12秒)", example = "45秒")
    private String duration;

    @Schema(description = "状态 (PENDING-待回拨, ASSIGNED-已派单, CALLED-已呼出)", example = "PENDING")
    private String status;

    @Schema(description = "指派坐席姓名", example = "舒欣")
    private String assignee;

    @Schema(description = "指派坐席工号", example = "901415")
    private String assigneeWorkNo;

    @Schema(description = "回访呼叫尝试次数", example = "1")
    private Integer callAttempts;

    @Schema(description = "最后回访时间", example = "2026-09-18 16:00:00")
    private String lastCalledAt;

    @Schema(description = "跟进备注", example = "首次回访占线")
    private String notes;
}
