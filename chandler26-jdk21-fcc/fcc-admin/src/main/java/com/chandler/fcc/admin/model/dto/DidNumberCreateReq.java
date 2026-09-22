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
 * 呼入 DID 引示号录入请求 DTO
 *
 * @author Chandler
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "DID呼入号码新增请求参数")
public class DidNumberCreateReq implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "电话号码不能为空")
    @Schema(description = "呼入接入号码", example = "01088889999")
    private String phoneNumber;

    @NotBlank(message = "呼入拨号上下文不能为空")
    @Pattern(
        regexp = "^[A-Za-z0-9_.-]{1,64}$",
        message = "呼入拨号上下文只能包含字母、数字、点、下划线或连字符"
    )
    @Schema(description = "FreeSWITCH 入局拨号计划上下文", example = "telecom")
    private String routingContext;

    @Schema(description = "业务路由 Key (匹配流程定义)", example = "ROUTING_DEFAULT_IVR")
    private String routeKey;
}
