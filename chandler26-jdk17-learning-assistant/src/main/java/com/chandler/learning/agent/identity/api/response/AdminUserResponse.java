package com.chandler.learning.agent.identity.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Data;

import java.time.LocalDateTime;

/** 系统管理用户列表项。密码和联系方式不在列表接口返回。 */
@Data
public class AdminUserResponse {

    @Schema(description = "主键 ID")
    private Long id;
    @Schema(description = "用户名")
    private String username;
    @Schema(description = "用户昵称")
    private String nickname;
    @Schema(description = "角色编码")
    private String roleCode;
    @Schema(description = "角色名称")
    private String roleLabel;
    @Schema(description = "是否启用")
    private Boolean enabled;
    @Schema(description = "学习计划数量")
    private Integer learningPlanCount;
    @Schema(description = "个人单词本数量")
    private Integer wordbookCount;
    @Schema(description = "最近登录时间")
    private LocalDateTime lastLoginTime;
    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
