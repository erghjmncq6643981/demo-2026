package com.chandler.learning.agent.domain.dto.learning;

import jakarta.validation.constraints.Size;
import lombok.Data;

/** 系统管理员修改用户请求。 */
@Data
public class AdminUserUpdateRequest {

    @Size(max = 64, message = "昵称不能超过 64 个字符")
    private String nickname;

    /** USER 或 ADMIN。 */
    private String roleCode;

    private Boolean enabled;
}
