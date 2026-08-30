package com.chandler.learning.agent.identity.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户账户响应数据。
 */
@Data
public class AuthResponse {

    @Schema(description = "登录令牌")
    private String token;

    @Schema(description = "过期时间")
    private LocalDateTime expiredTime;

    @Schema(description = "当前用户资料")
    private UserProfileResponse user;
}
