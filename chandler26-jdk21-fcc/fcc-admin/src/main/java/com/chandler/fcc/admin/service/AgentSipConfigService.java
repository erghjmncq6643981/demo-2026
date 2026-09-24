package com.chandler.fcc.admin.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chandler.fcc.admin.client.SidecarAdminClient;
import com.chandler.fcc.admin.infrastructure.persistence.entity.ExtensionEntity;
import com.chandler.fcc.admin.infrastructure.persistence.mapper.ExtensionMapper;
import com.chandler.fcc.admin.model.vo.AgentSipConfigVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 提供本人已开通 WebRTC 分机的配置，优先从 Go Sidecar 动态发现接入端点，拒绝任意工号查询。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentSipConfigService {
    private final ExtensionMapper extensions;
    private final AuthService auth;
    private final SipCredentialCipher cipher;
    private final SidecarAdminClient sidecarAdminClient;
    @Value("${fcc.sip.ws-url:}")
    private String configuredWsUrl;
    @Value("${fcc.sip.domain:}")
    private String configuredDomain;

    /** 查询当前认证主体的 WebRTC 分机配置。
     * @return 仅该坐席可用的注册配置
     */
    public AgentSipConfigVO current() {
        var user = auth.getLoginUserInfo();
        if (!AuthService.SUBJECT_AGENT.equals(user.getAccountType()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅坐席本人可读取注册配置");

        String effectiveWsUrl = resolveWsUrl();
        String effectiveDomain = resolveDomain();

        if (effectiveWsUrl.isBlank() || effectiveDomain.isBlank() || !Set.of("ws", "wss").contains(URI.create(effectiveWsUrl).getScheme()))
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "SIP 接入配置未完成");
        var matches = extensions.selectList(new LambdaQueryWrapper<ExtensionEntity>()
                .eq(ExtensionEntity::getAgentWorkNo, user.getLoginId())
                .eq(ExtensionEntity::getEndpointType, "WEBRTC")
                .eq(ExtensionEntity::getStatus, "ENABLED")
                .isNull(ExtensionEntity::getDeletedAt).last("LIMIT 2"));
        if (matches.size() != 1 || matches.getFirst().getCredentialSecret() == null)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "WebRTC 分机未开通或绑定不唯一");
        var extension = matches.getFirst();
        return new AgentSipConfigVO(extension.getExtension(), effectiveWsUrl, effectiveDomain,
                cipher.decrypt(extension.getCredentialSecret()));
    }

    private String resolveWsUrl() {
        if (configuredWsUrl != null && !configuredWsUrl.isBlank()) {
            return configuredWsUrl;
        }
        return discoverEndpointFromSidecar("WS_URL");
    }

    private String resolveDomain() {
        if (configuredDomain != null && !configuredDomain.isBlank()) {
            return configuredDomain;
        }
        return discoverEndpointFromSidecar("DOMAIN");
    }

    private String discoverEndpointFromSidecar(String type) {
        try {
            List<Map<String, Object>> profiles = sidecarAdminClient.getSofiaProfiles();
            for (Map<String, Object> p : profiles) {
                if ("internal".equalsIgnoreCase(String.valueOf(p.get("name")))) {
                    String bindIp = String.valueOf(p.get("bind_ip"));
                    if ("DOMAIN".equals(type)) {
                        return bindIp != null && !bindIp.isBlank() ? bindIp : "127.0.0.1";
                    }
                    Object wsPortObj = p.get("ws_port");
                    int wsPort = wsPortObj instanceof Number ? ((Number) wsPortObj).intValue() : 5066;
                    return "ws://" + bindIp + ":" + wsPort;
                }
            }
        } catch (Exception e) {
            log.warn("[AgentSipConfigService] 动态获取 Go Sidecar 端点失败: {}", e.getMessage());
        }
        return "";
    }
}
