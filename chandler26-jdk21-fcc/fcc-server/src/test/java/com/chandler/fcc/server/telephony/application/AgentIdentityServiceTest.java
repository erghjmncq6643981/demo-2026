package com.chandler.fcc.server.telephony.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.chandler.fcc.server.telephony.application.identity.IdentityProfile;
import com.chandler.fcc.server.telephony.application.port.IdentityProfilePort;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

/**
 * 验证运行服务只接受统一认证服务授予的身份与管理权限。
 */
class AgentIdentityServiceTest {

    /**
     * 清理线程绑定的模拟请求，避免影响其他测试。
     */
    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    /**
     * 控制台主体必须显式具备客户和自动外呼管理权限。
     */
    @Test
    void managementRequiresExplicitPermission() {
        IdentityProfilePort port = mock(IdentityProfilePort.class);
        AgentIdentityService service = new AgentIdentityService(port);
        bindToken("operator-token");
        when(port.authenticate("operator-token")).thenReturn(
            new IdentityProfile("operator", "CONSOLE", List.of("cdr:view"))
        );

        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            service::requireManagement
        );
        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    }

    /**
     * 具备精确权限或超级管理员通配权限的控制台主体可以进入管理用例。
     */
    @Test
    void managementAcceptsGrantedPermission() {
        IdentityProfilePort port = mock(IdentityProfilePort.class);
        AgentIdentityService service = new AgentIdentityService(port);
        bindToken("admin-token");
        when(port.authenticate("admin-token")).thenReturn(
            new IdentityProfile("admin", "CONSOLE", List.of("business:manage"))
        );
        assertEquals("admin", service.requireManagement().workNo());

        bindToken("super-token");
        when(port.authenticate("super-token")).thenReturn(
            new IdentityProfile("super", "CONSOLE", List.of("*"))
        );
        assertEquals("super", service.requireManagement().workNo());
    }

    /**
     * 坐席令牌即便权限字段异常包含管理码，也不能冒用控制台入口。
     */
    @Test
    void managementRejectsAgentAccountType() {
        IdentityProfilePort port = mock(IdentityProfilePort.class);
        AgentIdentityService service = new AgentIdentityService(port);
        bindToken("agent-token");
        when(port.authenticate("agent-token")).thenReturn(
            new IdentityProfile("901001", "AGENT", List.of("business:manage"))
        );

        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            service::requireManagement
        );
        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
    }

    /**
     * 将测试令牌绑定到当前请求上下文。
     *
     * @param token 测试令牌
     */
    private void bindToken(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("satoken", token);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }
}
