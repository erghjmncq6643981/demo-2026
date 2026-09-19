package com.chandler.fcc.server.flow.event;

import com.chandler.fcc.common.entity.CallInfoBO;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 呼叫接通桥接成功领域事件
 * <p>
 * 触发条件：双方话道成功执行 BRIDGE 桥接对讲。
 * </p>
 *
 * @author Chandler
 */
@Getter
public class CallConnectedEvent extends ApplicationEvent {

    private final CallInfoBO callInfo;

    /**
     * 构造呼叫接通事件
     *
     * @param callInfo 通话聚合根业务对象
     */
    public CallConnectedEvent(CallInfoBO callInfo) {
        super(callInfo);
        this.callInfo = callInfo;
    }

    @Override
    public CallInfoBO getSource() {
        return (CallInfoBO) super.getSource();
    }
}
