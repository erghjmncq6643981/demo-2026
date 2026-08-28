package com.chandler.learning.agent.identity.api.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 系统管理员新增用户请求。 */
@Data
public class AdminUserSaveRequest {

    @NotBlank(message = "用户名不能为空")
    @Size(max = 64, message = "用户名不能超过 64 个字符")
    @Schema(description = "名称")
    private String username;

    @NotBlank(message = "初始密码不能为空")
    @Size(min = 6, max = 128, message = "初始密码长度应为 6 至 128 个字符")
    @Schema(description = "密码")
    private String password;

    @Size(max = 64, message = "昵称不能超过 64 个字符")
    @Schema(description = "用户昵称")
    private String nickname;

    /** USER 或 ADMIN。 */
    @Schema(description = "编码")
    private String roleCode;

    @Schema(description = "是否启用")
    private Boolean enabled;
}
