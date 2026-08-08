package com.chandler.learning.agent.security;

import java.time.LocalDateTime;

/**
 * JwtClaims 类。
 */
/**
 * 处理 {@code JwtClaims} 相关业务。
 */
public record JwtClaims(Long userId, String username, LocalDateTime expiredTime) {
}
