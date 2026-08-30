package com.chandler.learning.agent.identity.api.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Size;
import lombok.Data;

/** 系统管理员修改用户请求。 */
@Data
public class AdminUserUpdateRequest {

    @Size(max = 64, message = "昵称不能超过 64 个字符")
    @Schema(description = "用户昵称")
    private String nickname;

    /** USER 或 ADMIN。 */
    @Schema(description = "角色编码")
    private String roleCode;

    @Schema(description = "是否启用")
    private Boolean enabled;
}
