package com.chandler.fcc.admin.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 外呼主叫号码录入请求 DTO
 *
 * @author Chandler
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "外呼主叫号码新增请求参数")
public class OutboundNumberCreateReq implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "电话号码不能为空")
    @Schema(description = "外呼展示电话号码", example = "02195588")
    private String phoneNumber;

    @NotNull(message = "中继ID不能为空")
    @Schema(description = "出局中继线路 ID", example = "3001")
    private Long trunkId;

    @Schema(description = "号码池编码", example = "default")
    @Builder.Default
    private String poolCode = "default";

    @Schema(description = "单号码并发限制", example = "2")
    @Builder.Default
    private Integer maxConcurrent = 1;
}
