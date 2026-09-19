package com.chandler.fcc.admin.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 动态系统配置项视图对象 VO
 *
 * @author Chandler
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "系统配置展示视图")
public class SystemConfigVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "配置主键 ID", example = "101")
    private Long id;

    @Schema(description = "配置参数名称 (Key)", example = "call.recording.auto-upload")
    private String propName;

    @Schema(description = "配置参数值 (Value)", example = "true")
    private String propValue;

    @Schema(description = "数据类型", example = "BOOLEAN")
    private String propType;

    @Schema(description = "配置作用域 (WEB, BACKEND, CLIENT, SYSTEM)", example = "BACKEND")
    private String scope;

    @Schema(description = "业务用途说明", example = "通话挂机后是否自动上传双轨录音至OSS")
    private String description;

    @Schema(description = "更新人", example = "admin")
    private String updatedBy;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
