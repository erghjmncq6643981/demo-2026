package com.chandler.fcc.admin.starter;

import com.chandler.fcc.admin.service.AgentSipConfigService;
import com.chandler.fcc.admin.service.AuthService;
import com.chandler.fcc.admin.service.SipCredentialCipher;
import com.chandler.fcc.admin.infrastructure.persistence.mapper.ExtensionMapper;
import com.chandler.fcc.admin.infrastructure.persistence.entity.ExtensionEntity;
import com.chandler.fcc.admin.model.vo.UserInfoVO;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

/** 配置接口只服务本人坐席，普通管理身份不得读取分机秘密。 */
class AgentSipConfigBoundaryTest {
    /** 控制台身份被拒绝，认证坐席仅取得查询到的本人配置。 */
    @Test void rejectsConsoleAndReturnsAuthenticatedConfiguration() {
        var mapper = mock(ExtensionMapper.class);
        var auth = mock(AuthService.class);
        var cipher = mock(SipCredentialCipher.class);
        var service = new AgentSipConfigService(mapper, auth, cipher);
        ReflectionTestUtils.setField(service, "wsUrl", "wss://sip.example/ws");
        ReflectionTestUtils.setField(service, "domain", "sip.example");
        when(auth.getLoginUserInfo()).thenReturn(UserInfoVO.builder().accountType("CONSOLE").build());
        assertThrows(ResponseStatusException.class, service::current);
        verifyNoInteractions(mapper, cipher);
        when(auth.getLoginUserInfo()).thenReturn(UserInfoVO.builder().accountType("AGENT").loginId("test-agent").build());
        byte[] encrypted = new byte[]{1};
        when(mapper.selectList(any())).thenReturn(List.of(ExtensionEntity.builder().extension("test-extension").credentialSecret(encrypted).build()));
        when(cipher.decrypt(encrypted)).thenReturn("test-only-secret");
        assertEquals("test-extension", service.current().getExtension());
        assertEquals("test-only-secret", service.current().getPassword());
    }
}
