package com.chandler.learning.agent.identity.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Data;

/**
 * UserProfileResponse 类。
 */
@Data
public class UserProfileResponse {

    @Schema(description = "主键标识")
    private Long id;

    @Schema(description = "用户名")
    private String username;

    @Schema(description = "用户昵称")
    private String nickname;

    @Schema(description = "脱敏手机号")
    private String phoneMasked;

    @Schema(description = "脱敏邮箱")
    private String emailMasked;

    /** 当前登录用户角色，用于渲染授权后的产品入口。 */
    @Schema(description = "角色编码")
    private String roleCode;

    /** 当前登录用户角色名称。 */
    @Schema(description = "角色名称")
    private String roleLabel;
}
