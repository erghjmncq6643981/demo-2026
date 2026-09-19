package com.chandler.fcc.admin.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 统一登录成功响应 VO
 *
 * @author Chandler
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "统一登录成功响应 VO")
public class LoginRespVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "Sa-Token 鉴权令牌 Token 字符串", example = "58a47fa2-4751-4e78-8318-7822ea8d88e0")
    private String tokenValue;

    @Schema(description = "Token 存储请求头 Key 名称", example = "satoken")
    private String tokenName;

    @Schema(description = "登录主体标识 (控制台账号为 username，坐席为工号)", example = "admin")
    private String loginId;

    @Schema(description = "账号主体类型 (CONSOLE 控制台账号, AGENT 坐席账号)", example = "CONSOLE")
    private String accountType;

    @Schema(description = "真实姓名/昵称", example = "系统超级管理员")
    private String realName;

    @Schema(description = "主角色编码 (ADMIN 超管, OPERATOR 运营, AUDITOR 审计, SUPERVISOR 班长主管, AGENT 普通坐席)", example = "ADMIN")
    private String role;

    @Schema(description = "权限码集合")
    private List<String> permissions;

    @Schema(description = "坐席绑定的通信分机号码 (仅坐席端返回)")
    private String extension;

    @Schema(description = "坐席绑定的接听方式 (WEBRTC, SIP, PHONE)", example = "WEBRTC")
    private String endpointType;
}
