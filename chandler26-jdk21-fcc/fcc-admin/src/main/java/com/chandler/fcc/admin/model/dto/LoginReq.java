package com.chandler.fcc.admin.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 用户与坐席统一登录请求 DTO
 *
 * @author Chandler
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "用户与坐席统一登录请求 DTO")
public class LoginReq implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "登录账号/工号不能为空")
    @Schema(description = "登录账号：控制台账号填 fcc_admin_user.username，坐席填 fcc_agent.work_no", example = "admin")
    private String username;

    @NotBlank(message = "登录密码不能为空")
    @Schema(description = "登录口令。口令由数据库中的 PBKDF2 派生串校验，不存在内置默认口令")
    private String password;

    @Schema(description = "登录终端模式标识 (仅用于前端引导，服务端按账号自动识别主体类型)", example = "ADMIN")
    private String loginType;
}
