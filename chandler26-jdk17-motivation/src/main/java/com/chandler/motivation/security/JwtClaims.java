package com.chandler.motivation.security;

import java.time.LocalDateTime;

public record JwtClaims(Long userId, String username, LocalDateTime expiredTime) {
}
