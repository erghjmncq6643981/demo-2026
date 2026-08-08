package com.chandler.learning.agent.domain.dto.learning;

import lombok.Data;

/**
 * UserProfileResponse 类。
 */
@Data
public class UserProfileResponse {

    private Long id;

    private String username;

    private String nickname;

    private String phoneMasked;

    private String emailMasked;
}
