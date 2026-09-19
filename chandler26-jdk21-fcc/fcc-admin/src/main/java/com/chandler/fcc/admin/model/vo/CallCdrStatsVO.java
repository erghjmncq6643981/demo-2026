package com.chandler.fcc.admin.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 通话话单指标聚合统计视图
 * <p>
 * 由数据库直接聚合得出，用于工作台顶部 KPI 卡片，
 * 与分页列表相互独立——卡片反映的是统计区间内的全量事实，
 * 不会因为列表分页而只统计到当前页。
 * </p>
 *
 * @author Chandler
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "通话话单指标聚合统计")
public class CallCdrStatsVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "统计区间内呼叫总数", example = "29")
    private long totalCalls;

    @Schema(description = "统计区间内已接通数", example = "29")
    private long answeredCalls;

    @Schema(description = "总通话时长 (秒)", example = "780")
    private long totalTalkSec;

    @Schema(description = "呼入通话时长 (秒)", example = "240")
    private long inboundTalkSec;

    @Schema(description = "呼出通话时长 (秒)", example = "540")
    private long outboundTalkSec;
}
