package com.chandler.fcc.server.event.handler;

import com.chandler.fcc.common.dto.event.EventRegistrationDTO;
import com.chandler.fcc.common.protocol.FccEventMethod;
import com.chandler.fcc.common.protocol.FccEventField;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 同步 SIP 分机注册态缓存并记录可读业务日志。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RegistrationEventHandler implements FccEventHandler {

    private static final String EXTENSION_PRESENCE_PREFIX = "fcc:extension:presence:";
    private static final Duration PRESENCE_LEASE = Duration.ofHours(1);

    private final StringRedisTemplate redis;

    /**
     * 判断是否为 SIP 注册状态事件。
     *
     * @param method 标准事件方法名
     * @return 是否支持
     */
    @Override
    public boolean supports(FccEventMethod method) {
        return method == FccEventMethod.REGISTRATION;
    }

    /**
     * 更新分机在线租约并记录结构化注册结果。
     *
     * @param params 注册事件参数
     */
    @Override
    public void handle(JsonNode params) {
        String extension = params.path("user").asText(null);
        String status = params.path("status").asText(null);
        if (extension == null || status == null) {
            throw new IllegalArgumentException("注册事件缺少 user 或 status");
        }

        String presence = "REGISTERED".equalsIgnoreCase(status) ? "ONLINE" : "OFFLINE";
        redis.opsForValue().set(EXTENSION_PRESENCE_PREFIX + extension, presence, PRESENCE_LEASE);

        EventRegistrationDTO registration = EventRegistrationDTO.builder()
            .nodeId(params.path(FccEventField.NODE_ID.getWireName()).asText(null))
            .user(extension)
            .domain(params.path("domain").asText(null))
            .status(status)
            .networkIp(params.path("network_ip").asText(null))
            .port(params.path("port").asInt(5060))
            .userAgent(params.path("user_agent").asText(null))
            .contact(params.path("contact").asText(null))
            .timestamp(
                params.path(FccEventField.SOURCE_TIMESTAMP.getWireName())
                    .asLong(System.currentTimeMillis())
            )
            .build();
        log.info(
            "[SIP 注册] nodeId={} extension={} status={} networkIp={}",
            registration.getNodeId(),
            extension,
            status,
            registration.getNetworkIp()
        );
    }
}
