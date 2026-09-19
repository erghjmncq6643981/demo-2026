package com.chandler.fcc.server.flow.event;

import com.chandler.fcc.common.entity.CallInfoBO;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 通话挂机结束领域事件
 * <p>
 * 触发条件：首个话道销毁 (DESTROY)，触发控制面停止录音、开启满意度按键评价或拆除关联话道。
 * </p>
 *
 * @author Chandler
 */
@Getter
public class CallEndEvent extends ApplicationEvent {

    private final CallInfoBO callInfo;

    /**
     * 构造呼叫挂断事件
     *
     * @param callInfo 通话聚合根业务对象
     */
    public CallEndEvent(CallInfoBO callInfo) {
        super(callInfo);
        this.callInfo = callInfo;
    }

    @Override
    public CallInfoBO getSource() {
        return (CallInfoBO) super.getSource();
    }
}
