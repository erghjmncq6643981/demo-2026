package com.chandler.learning.agent.domain.dto.learning;

import lombok.Data;

@Data
public class UserProfileResponse {

    private Long id;

    private String username;

    private String nickname;

    private String phoneMasked;

    private String emailMasked;
}
