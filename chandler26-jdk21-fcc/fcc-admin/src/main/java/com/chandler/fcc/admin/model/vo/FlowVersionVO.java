package com.chandler.fcc.admin.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 通话流程版本视图
 *
 * @author Chandler
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "流程版本视图")
public class FlowVersionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "版本名称", example = "v1.0.0")
    private String version;

    @Schema(description = "版本序号", example = "1")
    private Integer versionNo;

    @Schema(description = "发布状态 (DRAFT, PUBLISHED, ARCHIVED)", example = "PUBLISHED")
    private String publishStatus;

    @Schema(description = "版本编排定义 JSON", example = "{}")
    private String definitionJson;

    @Schema(description = "发布上线时间")
    private LocalDateTime publishedAt;

    @Schema(description = "创建人", example = "admin")
    private String createdBy;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
}
