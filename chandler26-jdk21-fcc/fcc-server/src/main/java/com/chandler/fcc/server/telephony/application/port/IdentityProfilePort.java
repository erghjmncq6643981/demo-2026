package com.chandler.fcc.server.telephony.application.port;

import com.chandler.fcc.server.telephony.application.identity.IdentityProfile;

/**
 * 从统一认证服务在线解析登录令牌的应用端口。
 */
public interface IdentityProfilePort {

    /**
     * 在线验证令牌并读取可信身份资料。
     *
     * @param token 登录令牌，不得写入日志
     * @return 认证服务返回的身份资料
     */
    IdentityProfile authenticate(String token);
}
