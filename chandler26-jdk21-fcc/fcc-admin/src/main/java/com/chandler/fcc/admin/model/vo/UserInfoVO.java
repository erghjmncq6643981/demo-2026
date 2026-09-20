package com.chandler.fcc.admin.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 当前登录用户信息 VO
 *
 * @author Chandler
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "当前登录用户信息 VO")
public class UserInfoVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "登录标识 ID (控制台账号为 username，坐席为工号)", example = "admin")
    private String loginId;

    @Schema(description = "登录主体所属租户标识")
    private Long tenantId;

    @Schema(description = "账号主体类型 (CONSOLE 控制台账号, AGENT 坐席账号)", example = "CONSOLE")
    private String accountType;

    @Schema(description = "用户真实姓名")
    private String realName;

    @Schema(description = "主角色", example = "SUPERVISOR")
    private String role;

    @Schema(description = "角色列表")
    private List<String> roles;

    @Schema(description = "权限码列表")
    private List<String> permissions;

    @Schema(description = "绑定分机", example = "1007")
    private String extension;

    @Schema(description = "接听方式", example = "WEBRTC")
    private String endpointType;
}
