package com.chandler.fcc.server.flow.event;

import com.chandler.fcc.common.entity.CallInfoBO;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 呼叫开始接入领域事件
 * <p>
 * 触发条件：客户呼入 (INBOUND) 且话道就绪，或者双向外呼首步启动。
 * </p>
 *
 * @author Chandler
 */
@Getter
public class CallStartEvent extends ApplicationEvent {

    private final CallInfoBO callInfo;

    /**
     * 构造呼叫开始事件
     *
     * @param callInfo 通话聚合根业务对象
     */
    public CallStartEvent(CallInfoBO callInfo) {
        super(callInfo);
        this.callInfo = callInfo;
    }

    @Override
    public CallInfoBO getSource() {
        return (CallInfoBO) super.getSource();
    }
}
