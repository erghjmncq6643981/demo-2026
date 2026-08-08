package com.chandler.learning.agent.domain.dto.learning;

import lombok.Data;

/**
 * UserProfileUpdateRequest 类。
 */
@Data
public class UserProfileUpdateRequest {

    private String nickname;

    private String phone;

    private String email;

    private String currentPassword;

    private String newPassword;
}
