package com.chandler.learning.agent.domain.dto.learning;

import lombok.Data;

import java.time.LocalDateTime;

/** 系统管理用户列表项。密码和联系方式不在列表接口返回。 */
@Data
public class AdminUserResponse {

    private Long id;
    private String username;
    private String nickname;
    private String roleCode;
    private String roleLabel;
    private Boolean enabled;
    private Integer learningPlanCount;
    private Integer wordbookCount;
    private LocalDateTime lastLoginTime;
    private LocalDateTime createTime;
}
