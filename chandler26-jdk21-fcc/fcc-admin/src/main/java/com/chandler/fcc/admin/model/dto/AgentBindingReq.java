package com.chandler.fcc.admin.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 坐席话机/分机绑定请求 DTO
 *
 * @author Chandler
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "坐席终端绑定请求参数")
public class AgentBindingReq implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "坐席ID不能为空")
    @Schema(description = "坐席雪花主键 ID", example = "10001")
    private Long agentId;

    @NotBlank(message = "终端类型不能为空")
    @Schema(description = "终端类型 (SIP, WEBRTC, MOBILE, PSTN)", example = "SIP")
    private String endpointType;

    @Schema(description = "关联的分机表主键 ID", example = "5001")
    private Long extensionId;

    @NotBlank(message = "终端标识值不能为空")
    @Schema(description = "终端标识值 (分机号如 1001，或手机号)", example = "1001")
    private String endpointValue;

    @Schema(description = "终端优先级 (0为最高)", example = "0")
    @Builder.Default
    private Integer priority = 0;

    @Schema(description = "生效起始时间")
    private LocalDateTime validFrom;

    @Schema(description = "失效截止时间")
    private LocalDateTime validTo;
}
