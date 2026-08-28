package com.chandler.learning.agent.identity.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * AuthResponse 类。
 */
@Data
public class AuthResponse {

    @Schema(description = "登录令牌")
    private String token;

    @Schema(description = "过期时间")
    private LocalDateTime expiredTime;

    @Schema(description = "业务属性")
    private UserProfileResponse user;
}
