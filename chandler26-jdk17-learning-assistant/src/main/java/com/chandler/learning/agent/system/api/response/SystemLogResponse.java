package com.chandler.learning.agent.system.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * SystemLogResponse 类。
 */
@Data
public class SystemLogResponse {

    @Schema(description = "主键标识")
    private Long id;

    @Schema(description = "业务类型")
    private String type;

    @Schema(description = "标题")
    private String title;

    @Schema(description = "详情")
    private String detail;

    @Schema(description = "来源")
    private String source;

    @Schema(description = "业务类型")
    private String businessType;

    @Schema(description = "业务标识")
    private String businessId;

    @Schema(description = "时间")
    private LocalDateTime time;
}
