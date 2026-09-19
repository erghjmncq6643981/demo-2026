package com.chandler.fcc.admin.model.enums;

import lombok.Getter;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * 控制台会话角色与权限策略
 * <p>
 * 定义登录成功后写入 Sa-Token 会话的角色码与权限码集合。
 * 角色来源全部取自数据库：{@code fcc_admin_user.role_code} 或 {@code fcc_agent.role_code}，
 * 代码中不再存在任何账号或人员的硬编码名单。
 * </p>
 *
 * @author Chandler
 */
@Getter
public enum AuthRoleEnum {

    /**
     * 系统超级管理员：拥有全部控制台权限
     */
    ADMIN("ADMIN", "系统超级管理员",
            List.of("ADMIN"),
            List.of("*")),

    /**
     * 运营管理员：管理坐席、流程、号码与线路资源，不具备强拆强插等实时干预能力
     */
    OPERATOR("OPERATOR", "运营管理员",
            List.of("OPERATOR"),
            List.of("agent:view", "agent:write", "flow:view", "flow:write", "resource:view", "resource:write", "cdr:view")),

    /**
     * 审计员：只读访问话单、录音与操作审计
     */
    AUDITOR("AUDITOR", "审计员",
            List.of("AUDITOR"),
            List.of("agent:view", "cdr:view", "audit:view")),

    /**
     * 班长/主管席：在坐席权限之上具备监听、耳语、强插、强拆等实时干预能力
     */
    SUPERVISOR("SUPERVISOR", "班长主管席",
            List.of("SUPERVISOR", "AGENT"),
            List.of("agent:ready", "agent:monitor", "agent:spy", "agent:coach", "agent:barge", "agent:kill", "cdr:view")),

    /**
     * 普通坐席：仅具备自身话务与话单查看权限
     */
    AGENT("AGENT", "普通坐席",
            List.of("AGENT"),
            List.of("agent:ready", "cdr:view"));

    /**
     * 角色码 (写入数据库与 Sa-Token 会话的稳定标识)
     */
    private final String code;

    /**
     * 中文角色名称
     */
    private final String displayName;

    /**
     * 授予 Sa-Token 的角色列表 (主管席同时具备 SUPERVISOR 与 AGENT)
     */
    private final List<String> roles;

    /**
     * 授予 Sa-Token 的权限码列表
     */
    private final List<String> permissions;

    AuthRoleEnum(String code, String displayName, List<String> roles, List<String> permissions) {
        this.code = code;
        this.displayName = displayName;
        this.roles = roles;
        this.permissions = permissions;
    }

    /**
     * 按角色码解析会话角色
     *
     * @param code 数据库中的角色码
     * @return 匹配的会话角色；无法识别时返回 {@link #AGENT} (最小权限兜底)
     */
    public static AuthRoleEnum ofCode(String code) {
        if (code == null || code.isBlank()) {
            return AGENT;
        }
        String normalized = code.trim().toUpperCase();
        return find(normalized).orElse(AGENT);
    }

    /**
     * 按角色码精确解析，用于需要区分「未识别角色」的场景
     *
     * @param code 数据库中的角色码
     * @return 匹配的会话角色 Optional
     */
    public static Optional<AuthRoleEnum> find(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        String normalized = code.trim().toUpperCase();
        return Arrays.stream(values())
                .filter(role -> role.code.equals(normalized))
                .findFirst();
    }

    /**
     * 判断给定角色码是否为控制台账号角色 (与坐席账号角色互斥)
     *
     * @param code 角色码
     * @return 属于控制台账号角色 (ADMIN/OPERATOR/AUDITOR) 返回 true
     */
    public static boolean isConsoleRole(String code) {
        return find(code)
                .map(role -> role == ADMIN || role == OPERATOR || role == AUDITOR)
                .orElse(false);
    }
}
