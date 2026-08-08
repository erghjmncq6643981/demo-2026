package com.chandler.learning.agent.domain.dto.learning;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * AuthResponse 类。
 */
@Data
public class AuthResponse {

    private String token;

    private LocalDateTime expiredTime;

    private UserProfileResponse user;
}
