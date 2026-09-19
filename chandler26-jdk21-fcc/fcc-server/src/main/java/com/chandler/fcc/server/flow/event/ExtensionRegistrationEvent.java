package com.chandler.fcc.server.flow.event;

import com.chandler.fcc.common.dto.event.EventRegistrationDTO;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * SIP 分机注册态生命周期领域事件
 * <p>
 * 触发条件：Sidecar 监听到 Sofia SIP 终端注册、注销或租约过期，广播供坐席状态同步与 WebSocket 推送。
 * </p>
 *
 * @author Chandler
 */
@Getter
public class ExtensionRegistrationEvent extends ApplicationEvent {

    private final EventRegistrationDTO registrationDTO;

    /**
     * 构造分机注册态事件
     *
     * @param source          事件发布源
     * @param registrationDTO 注册态事件传输对象
     */
    public ExtensionRegistrationEvent(Object source, EventRegistrationDTO registrationDTO) {
        super(source);
        this.registrationDTO = registrationDTO;
    }
}
