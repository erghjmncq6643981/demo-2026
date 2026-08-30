package com.chandler.learning.agent.security;

import java.time.LocalDateTime;

/**
 * 安全认证领域组件。
 */
/** 表示解析并校验后的 JWT 声明。 */
public record JwtClaims(Long userId, String username, LocalDateTime expiredTime) {
}
