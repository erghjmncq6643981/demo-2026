package com.chandler.fcc.server.flow.event;

import com.chandler.fcc.common.entity.CallInfoBO;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 呼叫振铃/呼叫中领域事件
 * <p>
 * 触发条件：外呼发起后接收到 FreeSWITCH CALLING / RINGING 事件。
 * </p>
 *
 * @author Chandler
 */
@Getter
public class CallCallingEvent extends ApplicationEvent {

    private final CallInfoBO callInfo;

    /**
     * 构造呼叫振铃中事件
     *
     * @param callInfo 通话聚合根业务对象
     */
    public CallCallingEvent(CallInfoBO callInfo) {
        super(callInfo);
        this.callInfo = callInfo;
    }

    @Override
    public CallInfoBO getSource() {
        return (CallInfoBO) super.getSource();
    }
}
