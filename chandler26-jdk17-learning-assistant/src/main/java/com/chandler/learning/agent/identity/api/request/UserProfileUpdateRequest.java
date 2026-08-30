package com.chandler.learning.agent.identity.api.request;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Data;

/**
 * 用户账户请求参数。
 */
@Data
public class UserProfileUpdateRequest {

    @Schema(description = "用户昵称")
    private String nickname;

    @Schema(description = "手机号")
    private String phone;

    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "当前密码")
    private String currentPassword;

    @Schema(description = "新密码")
    private String newPassword;
}
