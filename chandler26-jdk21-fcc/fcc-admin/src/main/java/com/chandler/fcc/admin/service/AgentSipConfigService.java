package com.chandler.fcc.admin.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chandler.fcc.admin.infrastructure.persistence.entity.ExtensionEntity;
import com.chandler.fcc.admin.infrastructure.persistence.mapper.ExtensionMapper;
import com.chandler.fcc.admin.model.vo.AgentSipConfigVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.util.Set;

/** 提供本人已开通 WebRTC 分机的配置，拒绝任意工号查询。 */
@Service
@RequiredArgsConstructor
public class AgentSipConfigService {
    private final ExtensionMapper extensions;
    private final AuthService auth;
    private final SipCredentialCipher cipher;
    @Value("${fcc.sip.ws-url:}")
    private String wsUrl;
    @Value("${fcc.sip.domain:}")
    private String domain;

    /** 查询当前认证主体的 WebRTC 分机配置。
     * @return 仅该坐席可用的注册配置
     */
    public AgentSipConfigVO current() {
        var user = auth.getLoginUserInfo();
        if (!AuthService.SUBJECT_AGENT.equals(user.getAccountType()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅坐席本人可读取注册配置");
        if (wsUrl.isBlank() || domain.isBlank() || !Set.of("ws", "wss").contains(URI.create(wsUrl).getScheme()))
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "SIP 接入配置未完成");
        var matches = extensions.selectList(new LambdaQueryWrapper<ExtensionEntity>()
                .eq(ExtensionEntity::getAgentWorkNo, user.getLoginId())
                .eq(ExtensionEntity::getEndpointType, "WEBRTC")
                .eq(ExtensionEntity::getStatus, "ENABLED")
                .isNull(ExtensionEntity::getDeletedAt).last("LIMIT 2"));
        if (matches.size() != 1 || matches.getFirst().getCredentialSecret() == null)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "WebRTC 分机未开通或绑定不唯一");
        var extension = matches.getFirst();
        return new AgentSipConfigVO(extension.getExtension(), wsUrl, domain,
                cipher.decrypt(extension.getCredentialSecret()));
    }
}
