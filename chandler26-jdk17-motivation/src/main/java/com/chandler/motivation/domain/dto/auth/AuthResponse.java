package com.chandler.motivation.domain.dto.auth;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class AuthResponse {
    private String token;
    private LocalDateTime expiredTime;
    private UserProfileResponse user;
}
