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

    /** 当前登录用户角色，用于渲染授权后的产品入口。 */
    private String roleCode;

    /** 当前登录用户角色名称。 */
    private String roleLabel;
}
