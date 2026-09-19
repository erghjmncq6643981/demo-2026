package com.chandler.fcc.admin.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 管理控制台账号创建请求 DTO
 *
 * @author Chandler
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "管理控制台账号创建请求参数")
public class AdminUserCreateReq implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "登录账号不能为空")
    @Size(max = 64, message = "登录账号长度不能超过 64")
    @Schema(description = "登录账号 (唯一)", example = "ops_zhang")
    private String username;

    @NotBlank(message = "账号姓名不能为空")
    @Size(max = 128, message = "账号姓名长度不能超过 128")
    @Schema(description = "账号显示姓名", example = "张运营")
    private String realName;

    @Schema(description = "控制台角色码 (ADMIN / OPERATOR / AUDITOR)，缺省为 OPERATOR", example = "OPERATOR")
    private String roleCode;

    @Size(min = 8, max = 64, message = "口令长度需在 8 到 64 之间")
    @Schema(description = "登录口令；留空则由系统生成一次性随机口令并在响应中返回", example = "Ops@2026#One")
    private String password;

    @Schema(description = "账号状态 (ENABLED / DISABLED)，缺省为 ENABLED", example = "ENABLED")
    private String status;
}
