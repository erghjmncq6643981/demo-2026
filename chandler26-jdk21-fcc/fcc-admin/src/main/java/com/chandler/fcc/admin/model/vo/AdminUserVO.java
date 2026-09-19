package com.chandler.fcc.admin.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 管理控制台账号视图对象 VO
 *
 * @author Chandler
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "管理控制台账号展示视图")
public class AdminUserVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "账号主键 ID")
    private Long id;

    @Schema(description = "登录账号")
    private String username;

    @Schema(description = "账号显示姓名")
    private String realName;

    @Schema(description = "控制台角色码")
    private String roleCode;

    @Schema(description = "控制台角色中文名")
    private String roleName;

    @Schema(description = "账号状态 (ENABLED / DISABLED)")
    private String status;

    @Schema(description = "是否已设置登录口令")
    private Boolean passwordConfigured;

    @Schema(description = "口令最近设置时间")
    private LocalDateTime passwordUpdatedAt;

    @Schema(description = "最近一次成功登录时间")
    private LocalDateTime lastLoginAt;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
}
