package com.chandler.fcc.admin.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 坐席终端绑定记录视图对象 VO
 *
 * @author Chandler
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "坐席终端绑定信息展示视图")
public class AgentBindingVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "绑定记录 ID", example = "6001")
    private Long id;

    @Schema(description = "坐席 ID", example = "10001")
    private Long agentId;

    @Schema(description = "坐席姓名", example = "钱丁君")
    private String agentName;

    @Schema(description = "坐席工号", example = "901001")
    private String workNo;

    @Schema(description = "终端类型 (SIP, WEBRTC, MOBILE, PSTN)", example = "SIP")
    private String endpointType;

    @Schema(description = "关联的分机表主键 ID", example = "5001")
    private Long extensionId;

    @Schema(description = "终端标识值 (如分机号 1001)", example = "1001")
    private String endpointValue;

    @Schema(description = "优先级", example = "0")
    private Integer priority;

    @Schema(description = "状态 (ENABLED, DISABLED)", example = "ENABLED")
    private String status;

    @Schema(description = "有效起始时间")
    private LocalDateTime validFrom;

    @Schema(description = "有效结束时间")
    private LocalDateTime validTo;
}
