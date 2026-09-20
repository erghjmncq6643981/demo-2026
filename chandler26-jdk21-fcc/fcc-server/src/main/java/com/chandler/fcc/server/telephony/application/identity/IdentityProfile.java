package com.chandler.fcc.server.telephony.application.identity;

import java.util.List;

/**
 * 管理服务在线认证后返回的可信身份资料。
 */
public class IdentityProfile {

    private final String loginId;
    private final String accountType;
    private final List<String> permissions;

    /**
     * 创建一份不可变的认证身份资料。
     *
     * @param loginId 登录主体标识
     * @param accountType 账户类型
     * @param permissions 当前会话权限码
     */
    public IdentityProfile(String loginId, String accountType, List<String> permissions) {
        this.loginId = loginId;
        this.accountType = accountType;
        this.permissions = permissions == null ? List.of() : List.copyOf(permissions);
    }

    /**
     * 获取登录主体标识。
     *
     * @return 登录主体标识
     */
    public String getLoginId() {
        return loginId;
    }

    /**
     * 获取账户类型。
     *
     * @return 账户类型
     */
    public String getAccountType() {
        return accountType;
    }

    /**
     * 获取本次在线认证得到的权限码。
     *
     * @return 不可变权限码列表
     */
    public List<String> getPermissions() {
        return permissions;
    }
}
