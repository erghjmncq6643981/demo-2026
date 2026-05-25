package com.chandler.motivation.domain.dto.auth;

import lombok.Data;

@Data
public class UserProfileResponse {
    private Long id;
    private String username;
    private String nickname;
    private String avatarUrl;
    private String userType;
}
