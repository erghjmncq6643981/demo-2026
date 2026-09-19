package com.chandler.fcc.admin.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 通话流程版本正式发布上线入参
 *
 * @author Chandler
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "流程发布上线入参")
public class FlowPublishReq implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "发布的版本号", example = "v1.1.0", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "发布版本号不能为空")
    private String version;

    @Schema(description = "发布说明与备注", example = "支持按2业务线司机热线HTTP回调800ms熔断降级")
    private String remark;
}
