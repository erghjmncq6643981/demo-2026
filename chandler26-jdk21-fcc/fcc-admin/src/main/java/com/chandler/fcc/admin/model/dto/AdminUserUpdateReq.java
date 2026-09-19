package com.chandler.fcc.admin.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 管理控制台账号更新请求 DTO
 * <p>
 * 登录账号一经创建不可修改，口令调整请使用重置或修改口令接口。
 * </p>
 *
 * @author Chandler
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "管理控制台账号更新请求参数")
public class AdminUserUpdateReq implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "账号主键 ID 不能为空")
    @Schema(description = "账号主键 ID")
    private Long id;

    @Size(max = 128, message = "账号姓名长度不能超过 128")
    @Schema(description = "账号显示姓名")
    private String realName;

    @Schema(description = "控制台角色码 (ADMIN / OPERATOR / AUDITOR)")
    private String roleCode;

    @Schema(description = "账号状态 (ENABLED / DISABLED)")
    private String status;
}
