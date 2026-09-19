package com.chandler.fcc.admin.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 通信中继视图对象 VO
 *
 * @author Chandler
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "通信中继详情展示视图")
public class TrunkVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "中继主键 ID", example = "3001")
    private Long id;

    @Schema(description = "中继唯一编码", example = "TRUNK_UNICOM_01")
    private String trunkCode;

    @Schema(description = "中继名称", example = "中国联通核心SIP中继01")
    private String trunkName;

    @Schema(description = "运营商代码", example = "CUCC")
    private String carrierCode;

    @Schema(description = "Sofia 网关名称", example = "gw_unicom_01")
    private String gatewayName;

    @Schema(description = "呼叫方向", example = "BOTH")
    private String direction;

    @Schema(description = "中继状态 (ENABLED, DISABLED)", example = "ENABLED")
    private String status;

    @Schema(description = "最大并发上限", example = "100")
    private Integer maxConcurrent;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
}
