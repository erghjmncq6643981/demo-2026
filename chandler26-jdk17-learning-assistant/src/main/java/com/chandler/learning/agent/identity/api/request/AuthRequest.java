package com.chandler.learning.agent.identity.api.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 用户账户请求参数。
 */
@Data
public class AuthRequest {

    @NotBlank(message = "用户名不能为空")
    @Schema(description = "登录用户名")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Schema(description = "密码")
    private String password;

    @Schema(description = "用户昵称")
    private String nickname;
}
