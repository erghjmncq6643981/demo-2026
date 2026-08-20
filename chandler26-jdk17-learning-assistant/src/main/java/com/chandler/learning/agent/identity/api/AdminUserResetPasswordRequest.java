package com.chandler.learning.agent.identity.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 系统管理员重置用户密码请求。 */
@Data
public class AdminUserResetPasswordRequest {

    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 128, message = "新密码长度应为 6 至 128 个字符")
    private String password;
}
