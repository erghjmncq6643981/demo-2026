package com.chandler.fcc.admin.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 外呼主叫号码视图对象 VO
 *
 * @author Chandler
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "外呼主叫号码展示视图")
public class OutboundNumberVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "主叫号码主键 ID", example = "5001")
    private Long id;

    @Schema(description = "电话号码", example = "02195588")
    private String phoneNumber;

    @Schema(description = "出局中继线路 ID", example = "3001")
    private Long trunkId;

    @Schema(description = "号码池编码", example = "default")
    private String poolCode;

    @Schema(description = "号码状态 (AVAILABLE, RESTING, SUSPENDED)", example = "AVAILABLE")
    private String status;

    @Schema(description = "最大并发限制", example = "2")
    private Integer maxConcurrent;

    @Schema(description = "录入时间")
    private LocalDateTime createdAt;
}
