package com.chandler.learning.agent.domain.dto.learning;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AuthResponse {

    private String token;

    private LocalDateTime expiredTime;

    private UserProfileResponse user;
}
