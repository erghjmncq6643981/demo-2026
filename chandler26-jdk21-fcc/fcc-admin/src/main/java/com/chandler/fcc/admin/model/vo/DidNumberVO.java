package com.chandler.fcc.admin.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 呼入 DID 引示号视图对象 VO
 *
 * @author Chandler
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "DID引示号展示视图")
public class DidNumberVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "DID 主键 ID", example = "4001")
    private Long id;

    @Schema(description = "电话号码", example = "01088889999")
    private String phoneNumber;

    @Schema(description = "关联中继线路 ID", example = "3001")
    private Long trunkId;

    @Schema(description = "业务路由 Key", example = "ROUTING_DEFAULT_IVR")
    private String routeKey;

    @Schema(description = "号码状态 (ENABLED, DISABLED)", example = "ENABLED")
    private String status;

    @Schema(description = "录入时间")
    private LocalDateTime createdAt;
}
