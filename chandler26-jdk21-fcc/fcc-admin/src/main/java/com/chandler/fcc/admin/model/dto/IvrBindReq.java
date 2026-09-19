package com.chandler.fcc.admin.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 实体话机 0000 语音工号自助绑定入参
 *
 * @author Chandler
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "话机语音自助绑定工号入参")
public class IvrBindReq implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "分机号 (话机已注册分机)", example = "1001", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "分机号不能为空")
    private String extension;

    @Schema(description = "坐席输入的工号", example = "901001", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "坐席工号不能为空")
    private String workNo;
}
