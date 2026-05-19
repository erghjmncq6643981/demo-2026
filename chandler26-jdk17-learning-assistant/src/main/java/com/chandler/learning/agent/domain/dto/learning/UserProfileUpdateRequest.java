package com.chandler.learning.agent.domain.dto.learning;

import lombok.Data;

@Data
public class UserProfileUpdateRequest {

    private String nickname;

    private String currentPassword;

    private String newPassword;
}
