package com.chandler.learning.agent.domain.dto.learning;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * AuthRequest 类。
 */
@Data
public class AuthRequest {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;

    private String nickname;
}
