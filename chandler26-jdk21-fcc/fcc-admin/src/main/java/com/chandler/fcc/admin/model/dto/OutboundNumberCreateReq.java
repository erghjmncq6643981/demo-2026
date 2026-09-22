package com.chandler.fcc.admin.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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

    @NotBlank(message = "出局拨号上下文不能为空")
    @Pattern(
        regexp = "^[A-Za-z0-9_.-]{1,64}$",
        message = "出局拨号上下文只能包含字母、数字、点、下划线或连字符"
    )
    @Schema(description = "FreeSWITCH 出局拨号计划上下文", example = "mobile")
    private String routingContext;

    @Schema(description = "号码池编码", example = "default")
    @Builder.Default
    private String poolCode = "default";

    @Schema(description = "单号码并发限制", example = "2")
    @Builder.Default
    private Integer maxConcurrent = 1;
}
