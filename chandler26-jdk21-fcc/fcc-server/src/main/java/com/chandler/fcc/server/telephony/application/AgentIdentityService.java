package com.chandler.fcc.server.telephony.application;

import com.chandler.fcc.server.telephony.application.identity.IdentityProfile;
import com.chandler.fcc.server.telephony.application.port.IdentityProfilePort;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

/**
 * 通过管理服务验证坐席令牌，控制面不信任调用方声明的工号。
 */
@Service
@RequiredArgsConstructor
public class AgentIdentityService {

    private static final String ACCOUNT_AGENT = "AGENT";
    private static final String ACCOUNT_CONSOLE = "CONSOLE";
    private static final String BUSINESS_MANAGE = "business:manage";

    private final IdentityProfilePort identityProfilePort;

    /**
     * 验证当前 HTTP 请求的坐席身份及显式工号。
     *
     * @param requestedWorkNo 请求工号，可为空但不可冒用
     * @return 认证坐席工号
     */
    public String requireAgent(String requestedWorkNo) {
        var attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        String token = attributes == null ? null : attributes.getRequest().getHeader("satoken");
        String workNo = authenticate(token);
        if (requestedWorkNo != null && !requestedWorkNo.isBlank() && !workNo.equals(requestedWorkNo)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "坐席身份不匹配");
        }
        return workNo;
    }

    /**
     * 对令牌进行在线认证，不缓存过期或已注销的身份。
     *
     * @param token 登录令牌，不得记录到日志
     * @return 认证坐席工号
     */
    public String authenticate(String token) {
        return authenticateProfile(token, ACCOUNT_AGENT).getLoginId();
    }

    /**
     * 已认证业务主体，仅由身份服务产生。
     *
     * @param workNo 坐席工号或管理账号
     */
    public record Principal(String workNo) {}

    /**
     * 验证当前请求并取得业务主体。
     *
     * @return 当前请求的可信业务主体
     */
    public Principal requirePrincipal() {
        var attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return authenticatePrincipal(
            attributes == null ? null : attributes.getRequest().getHeader("satoken")
        );
    }

    /**
     * 在线验证长连接身份。
     *
     * @param token 登录令牌，不得记录
     * @return 可信坐席主体
     */
    public Principal authenticatePrincipal(String token) {
        IdentityProfile profile = authenticateProfile(token, ACCOUNT_AGENT);
        return new Principal(profile.getLoginId());
    }

    /**
     * 管理端通过在线身份核验进入运行业务管理入口。
     *
     * @return 已认证管理主体
     */
    public Principal requireManagement() {
        var attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        IdentityProfile profile = authenticateProfile(
            attributes == null ? null : attributes.getRequest().getHeader("satoken"),
            ACCOUNT_CONSOLE
        );
        if (
            !profile.getPermissions().contains(BUSINESS_MANAGE) &&
            !profile.getPermissions().contains("*")
        ) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "缺少客户和自动外呼管理权限");
        }
        return new Principal(profile.getLoginId());
    }

    /**
     * 按账户类型在线验证，不允许坐席令牌冒用管理入口。
     *
     * @param token 登录令牌
     * @param accountType 必须匹配的账户类型
     * @return 身份资料
     */
    private IdentityProfile authenticateProfile(String token, String accountType) {
        if (token == null || token.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
        }
        IdentityProfile profile = identityProfilePort.authenticate(token);
        if (
            !accountType.equals(profile.getAccountType()) ||
            profile.getLoginId() == null ||
            profile.getLoginId().isBlank()
        ) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "登录已失效");
        }
        return profile;
    }
}
