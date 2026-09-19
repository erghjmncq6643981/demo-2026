package com.chandler.fcc.admin.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 通信分机分页检索请求 DTO
 *
 * @author Chandler
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "分机分页检索请求参数")
public class ExtensionQueryReq implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "分机号码 (模糊检索)", example = "100")
    private String extension;

    @Schema(description = "状态 (ENABLED, DISABLED)", example = "ENABLED")
    private String status;

    @Schema(description = "当前页码", example = "1")
    @Builder.Default
    private long pageNum = 1;

    @Schema(description = "每页大小", example = "20")
    @Builder.Default
    private long pageSize = 20;
}
