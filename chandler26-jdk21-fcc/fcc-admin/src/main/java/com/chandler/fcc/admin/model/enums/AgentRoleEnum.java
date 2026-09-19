package com.chandler.fcc.admin.model.enums;

import lombok.Getter;

import java.util.Arrays;
import java.util.Optional;

/**
 * 坐席业务角色枚举 (fcc_agent.role_code)
 * <p>
 * 与 {@link AuthRoleEnum} 的区别：本枚举描述坐席在呼叫中心业务中的岗位，
 * 而 {@link AuthRoleEnum} 描述登录后在 Sa-Token 会话中生效的权限角色。
 * 二者通过 {@link #toAuthRole()} 建立映射。
 * </p>
 *
 * @author Chandler
 */
@Getter
public enum AgentRoleEnum {

    /**
     * 坐席班长/主管 (历史上等价于 AGENT_ADMIN)
     */
    AGENT_ADMIN("AGENT_ADMIN", "坐席班长/主管", AuthRoleEnum.SUPERVISOR),

    /**
     * 值班长 (与班长同权限，用于排班别名)
     */
    SUPERVISOR("SUPERVISOR", "值班长", AuthRoleEnum.SUPERVISOR),

    /**
     * 普通坐席
     */
    AGENT_MEMBER("AGENT_MEMBER", "普通坐席", AuthRoleEnum.AGENT),

    /**
     * 兼容别名：普通坐席
     */
    AGENT("AGENT", "普通坐席", AuthRoleEnum.AGENT);

    /**
     * 角色码 (写入 fcc_agent.role_code)
     */
    private final String code;

    /**
     * 中文岗位名称
     */
    private final String displayName;

    /**
     * 对应的会话权限角色
     */
    private final AuthRoleEnum authRole;

    AgentRoleEnum(String code, String displayName, AuthRoleEnum authRole) {
        this.code = code;
        this.displayName = displayName;
        this.authRole = authRole;
    }

    /**
     * 新增坐席时使用的默认角色码
     */
    public static final String DEFAULT_CODE = "AGENT_MEMBER";

    /**
     * 按角色码解析坐席岗位
     *
     * @param code 数据库中的角色码
     * @return 匹配的坐席岗位 Optional
     */
    public static Optional<AgentRoleEnum> find(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        String normalized = code.trim().toUpperCase();
        return Arrays.stream(values())
                .filter(role -> role.code.equals(normalized))
                .findFirst();
    }

    /**
     * 将坐席角色码解析为会话权限角色
     * <p>
     * 取值为空或无法识别时按普通坐席处理，避免因脏数据获得过高权限。
     * </p>
     *
     * @param code 数据库中的坐席角色码
     * @return 会话权限角色
     */
    public static AuthRoleEnum toAuthRole(String code) {
        return find(code)
                .map(AgentRoleEnum::getAuthRole)
                .orElse(AuthRoleEnum.AGENT);
    }

    /**
     * 归一化坐席角色码；空值或非法值统一回落到 {@link #DEFAULT_CODE}
     *
     * @param code 待归一化的角色码
     * @return 合法的坐席角色码
     */
    public static String normalizeCode(String code) {
        return find(code).map(AgentRoleEnum::getCode).orElse(DEFAULT_CODE);
    }
}
