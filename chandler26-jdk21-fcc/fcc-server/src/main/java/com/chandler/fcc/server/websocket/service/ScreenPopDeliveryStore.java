package com.chandler.fcc.server.websocket.service;

import com.chandler.fcc.server.websocket.persistence.ScreenPopDeliveryMapper;
import com.chandler.fcc.common.dto.WsMessageDTO;
import com.chandler.fcc.common.dto.IncomingScreenPopDTO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Set;

/** 服务端持久化弹屏生命周期；重连补发不延长原来的振铃期限。 */
@Service
@RequiredArgsConstructor
public class ScreenPopDeliveryStore {
    private final ScreenPopDeliveryMapper mapper;
    private final ObjectMapper json = new ObjectMapper();

    /** 在发送前保存原始封套与过期时间。
     * @param tenant 通话租户
     * @param message 已授权的弹屏封套
     */
    public void save(long tenant, WsMessageDTO<IncomingScreenPopDTO> message) {
        int seconds = message.getData().getRingTimeoutSeconds() == null ? 30
                : Math.max(1, Math.min(120, message.getData().getRingTimeoutSeconds()));
        try {
            mapper.save(tenant, message.getWorkNo(), message.getCallId(), json.writeValueAsString(message),
                    message.getTimestamp() + seconds * 1000L);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("弹屏封套序列化失败", e);
        }
    }

    /** 仅补发本人仍在振铃且未过期的弹屏。
     * @param tenant 认证租户
     * @param owner 认证工号
     * @return 原始 JSON 封套，最多二十条
     */
    public List<String> pending(long tenant, String owner) { return mapper.pending(tenant, owner); }

    /** 保存终端回执，展示成功不表示话机接通。
     * @param tenant 认证租户
     * @param owner 认证工号
     * @param callId 通话标识
     * @param state RECEIVED、SHOWN、ACTIVATED 或 UNSUPPORTED
     */
    public void receipt(long tenant, String owner, String callId, String state) {
        if (callId == null || !callId.matches("[0-9]{1,20}")
                || !Set.of("RECEIVED", "SHOWN", "ACTIVATED", "UNSUPPORTED").contains(state)) return;
        mapper.receipt(tenant, owner, callId, state);
    }

    /** 接听、拒接或挂机后关闭指定收件人的提醒。
     * @param tenant 通话租户
     * @param owner 收件坐席
     * @param callId 通话标识
     */
    public void close(long tenant, String owner, String callId) { mapper.close(tenant, owner, callId); }
}
