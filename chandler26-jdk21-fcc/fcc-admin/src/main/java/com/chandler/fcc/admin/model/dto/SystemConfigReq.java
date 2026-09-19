package com.chandler.fcc.admin.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 动态系统配置项新增/修改请求 DTO
 *
 * @author Chandler
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "系统配置修改请求参数")
public class SystemConfigReq implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "配置参数名称不能为空")
    @Schema(description = "配置参数名称 (Key)", example = "call.recording.auto-upload")
    private String propName;

    @NotBlank(message = "配置参数值不能为空")
    @Schema(description = "配置参数取值 (Value)", example = "true")
    private String propValue;

    @Schema(description = "数据类型 (STRING, JSON, INT, BOOLEAN)", example = "BOOLEAN")
    @Builder.Default
    private String propType = "STRING";

    @Schema(description = "配置作用域 (WEB, BACKEND, CLIENT, SYSTEM)", example = "BACKEND")
    @Builder.Default
    private String scope = "BACKEND";

    @Schema(description = "参数中文业务含义说明", example = "通话挂机后是否自动上传双轨录音至OSS")
    private String description;
}
