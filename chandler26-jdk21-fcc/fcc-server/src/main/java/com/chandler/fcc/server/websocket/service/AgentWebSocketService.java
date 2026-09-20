package com.chandler.fcc.server.websocket.service;

import com.chandler.fcc.common.dto.IncomingScreenPopDTO;
import com.chandler.fcc.common.dto.WsMessageDTO;
import com.chandler.fcc.common.enums.WsMessageTypeEnum;
import com.chandler.fcc.server.websocket.handler.AgentWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Set;

/**
 * 坐席 WebSocket 业务推送综合服务
 * 封装高优先级来电弹屏、通话接通、挂机及广播事件下发
 *
 * @author Chandler
 * @version 1.0.0
 * @since 2026-09-18
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentWebSocketService {

    private final AgentWebSocketHandler webSocketHandler;
    private final ScreenPopDeliveryStore deliveries;

    /**
     * 推送话务弹屏事件至指定坐席
     *
     * @param workNo 坐席工号
     * @param screenPop 弹屏数据 (呼入/呼出共用)
     * @return 成功接收的终端会话数
     */
    public int pushScreenPop(String workNo, IncomingScreenPopDTO screenPop) {
        if (screenPop == null) {
            log.warn("⚠️ [WebSocket Service] 弹屏数据为空，跳过下发: workNo={}", workNo);
            return 0;
        }

        WsMessageDTO<IncomingScreenPopDTO> message = WsMessageDTO.of(
                WsMessageTypeEnum.CALL_SCREEN_POP.getCode(),
                workNo,
                screenPop.getCallId(),
                screenPop
        );

        try { deliveries.save(message); }
        catch (RuntimeException failure) { log.error("[弹屏] 持久投递记录失败 callId={}", screenPop.getCallId()); }

        log.info("[WebSocket Service] 推送弹屏 workNo={} callId={} direction={}",
                workNo, screenPop.getCallId(), screenPop.getDirection());

        return webSocketHandler.sendToWorkNo(workNo, message);
    }

    /**
     * 推送通话已接通事件
     *
     * @param workNo 坐席工号
     * @param callId 呼叫ID
     * @param data 附加数据
     * @return 成功会话数
     */
    public int pushCallAnswered(String workNo, String callId, Object data) {
        closeDelivery(workNo, callId);
        WsMessageDTO<Object> message = WsMessageDTO.of(
                WsMessageTypeEnum.CALL_ANSWERED.getCode(),
                workNo,
                callId,
                data
        );
        return webSocketHandler.sendToWorkNo(workNo, message);
    }

    /**
     * 推送通话已挂断事件
     *
     * @param workNo 坐席工号
     * @param callId 呼叫ID
     * @param data 附加数据
     * @return 成功会话数
     */
    public int pushCallHangup(String workNo, String callId, Object data) {
        closeDelivery(workNo, callId);
        WsMessageDTO<Object> message = WsMessageDTO.of(
                WsMessageTypeEnum.CALL_HANGUP.getCode(),
                workNo,
                callId,
                data
        );
        return webSocketHandler.sendToWorkNo(workNo, message);
    }

    /**
     * 推送通用消息至指定坐席工号
     *
     * @param workNo 坐席工号
     * @param message 消息封套
     * @return 成功会话数
     */
    public int sendToWorkNo(String workNo, WsMessageDTO<?> message) {
        return webSocketHandler.sendToWorkNo(workNo, message);
    }

    /** 关闭提醒记录；记录故障不能阻断通话事件送达。
     * @param workNo 坐席 @param callId 通话
     */
    private void closeDelivery(String workNo, String callId) {
        try { deliveries.close(workNo, callId); }
        catch (RuntimeException failure) { log.error("[弹屏] 关闭投递记录失败 callId={}", callId); }
    }

    /**
     * 全局广播消息至所有在线坐席
     *
     * @param message 消息封套
     * @return 成功广播数
     */
    public int broadcast(WsMessageDTO<?> message) {
        return webSocketHandler.broadcast(message);
    }

    /**
     * 获取所有当前在线的坐席工号集合
     */
    public Set<String> getOnlineWorkNos() {
        return webSocketHandler.getOnlineWorkNos();
    }

    /**
     * 获取当前活动的 WebSocket 连接总数
     */
    public int getTotalSessionCount() {
        return webSocketHandler.getTotalSessionCount();
    }

    /**
     * 判断指定坐席是否存在活跃终端会话
     *
     * @param workNo 坐席工号
     * @return true 表示至少有一个在线终端
     */
    public boolean isAgentOnline(String workNo) {
        return StringUtils.hasText(workNo) && webSocketHandler.getTotalSessionCount() > 0
                && webSocketHandler.getOnlineWorkNos().contains(workNo);
    }
}
