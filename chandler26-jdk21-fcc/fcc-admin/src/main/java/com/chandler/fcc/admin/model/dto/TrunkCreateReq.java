package com.chandler.fcc.admin.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * SIP 中继线路与运营商网关创建请求 DTO
 *
 * @author Chandler
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "通信中继创建请求参数")
public class TrunkCreateReq implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "中继编码不能为空")
    @Schema(description = "中继唯一编码", example = "TRUNK_UNICOM_01")
    private String trunkCode;

    @NotBlank(message = "中继名称不能为空")
    @Schema(description = "中继中文名称", example = "中国联通核心SIP中继01")
    private String trunkName;

    @Schema(description = "运营商代码 (CUCC, CMCC, CTCC)", example = "CUCC")
    private String carrierCode;

    @NotBlank(message = "网关名称不能为空")
    @Schema(description = "FreeSWITCH Sofia 网关名称", example = "gw_unicom_01")
    private String gatewayName;

    @Schema(description = "呼叫方向 (INBOUND, OUTBOUND, BOTH)", example = "BOTH")
    @Builder.Default
    private String direction = "BOTH";

    @Schema(description = "最大并发限制 (0为无限制)", example = "100")
    @Builder.Default
    private Integer maxConcurrent = 0;

    @Schema(description = "网关扩展配置 JSON", example = "{}")
    private String configJson;
}
