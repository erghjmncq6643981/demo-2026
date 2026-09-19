package com.chandler.fcc.admin.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 坐席替班代接记录视图对象 VO
 *
 * @author Chandler
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "坐席替班代接记录展示视图")
public class AgentSubstituteVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "替班记录雪花主键 ID", example = "8001")
    private Long id;

    @Schema(description = "申请人坐席 ID", example = "10001")
    private Long applicantAgentId;

    @Schema(description = "申请人坐席姓名", example = "钱丁君")
    private String applicantName;

    @Schema(description = "代接坐席 ID", example = "10002")
    private Long substituteAgentId;

    @Schema(description = "代接坐席姓名", example = "陈松")
    private String substituteName;

    @Schema(description = "替班模式 (PP / PG)", example = "PP")
    private String substituteType;

    @Schema(description = "替班场景 (NIGHT_OFF, TEMPORARY_LEAVE)", example = "NIGHT_OFF")
    private String scope;

    @Schema(description = "替班起始时间")
    private LocalDateTime startTime;

    @Schema(description = "替班结束时间")
    private LocalDateTime endTime;

    @Schema(description = "状态 (UNCONFIRM-待确认, CONFIRMED-已确认, CANCELLED-已取消)", example = "CONFIRMED")
    private String status;

    @Schema(description = "申请事由", example = "值夜班代接")
    private String reason;

    @Schema(description = "确认生效时间")
    private LocalDateTime confirmedAt;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
}
