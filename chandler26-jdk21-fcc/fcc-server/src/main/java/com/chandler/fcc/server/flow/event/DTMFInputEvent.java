package com.chandler.fcc.server.flow.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 用户按键 DTMF 输入领域事件
 * <p>
 * 触发条件：话道捕获到电话按键，广播至 Spring 事件总线供 IVR 或满意度模块响应。
 * </p>
 *
 * @author Chandler
 */
@Getter
public class DTMFInputEvent extends ApplicationEvent {

    private final String ctrlUuid;
    private final String uuid;
    private final String digit;
    private final int durationMs;

    /**
     * 构造 DTMF 按键输入事件
     *
     * @param source     事件发布源
     * @param ctrlUuid   控制流程标识
     * @param uuid       产生按键的话道 UUID
     * @param digit      按键字符
     * @param durationMs 按键持续时长（毫秒）
     */
    public DTMFInputEvent(Object source, String ctrlUuid, String uuid, String digit, int durationMs) {
        super(source);
        this.ctrlUuid = ctrlUuid;
        this.uuid = uuid;
        this.digit = digit;
        this.durationMs = durationMs;
    }
}
