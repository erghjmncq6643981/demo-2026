package com.chandler.fcc.admin.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 坐席替班与夜班代接申请请求 DTO
 *
 * @author Chandler
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "坐席替班代接申请参数")
public class AgentSubstituteReq implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "申请人坐席ID不能为空")
    @Schema(description = "申请人坐席 ID", example = "10001")
    private Long applicantAgentId;

    @NotNull(message = "替班代接坐席ID不能为空")
    @Schema(description = "替班代接坐席 ID", example = "10002")
    private Long substituteAgentId;

    @Schema(description = "替班类型 (PP-点对点, PG-技能组)", example = "PP")
    @Builder.Default
    private String substituteType = "PP";

    @Schema(description = "替班业务场景 (NIGHT_OFF-夜班, TEMPORARY_LEAVE-临时事假)", example = "NIGHT_OFF")
    @Builder.Default
    private String scope = "NIGHT_OFF";

    @NotNull(message = "替班起始时间不能为空")
    @Schema(description = "替班开始时间")
    private LocalDateTime startTime;

    @NotNull(message = "替班结束时间不能为空")
    @Schema(description = "替班结束时间")
    private LocalDateTime endTime;

    @Schema(description = "替班申请事由", example = "值夜班替班代接")
    private String reason;

    @Schema(description = "优先级 (默认 99)", example = "99")
    @Builder.Default
    private Integer priority = 99;
}
