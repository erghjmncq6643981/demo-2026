package com.chandler.fcc.server.flow.event;

import com.chandler.fcc.common.entity.CallInfoBO;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 呼叫路由与排队调度领域事件
 * <p>
 * 触发条件：单侧话道就绪后需要进一步调度对端话道（如 IVR 选路、排队分配坐席或外呼客户）。
 * </p>
 *
 * @author Chandler
 */
@Getter
public class CallRouteEvent extends ApplicationEvent {

    private final CallInfoBO callInfo;

    /**
     * 构造呼叫路由事件
     *
     * @param callInfo 通话聚合根业务对象
     */
    public CallRouteEvent(CallInfoBO callInfo) {
        super(callInfo);
        this.callInfo = callInfo;
    }

    @Override
    public CallInfoBO getSource() {
        return (CallInfoBO) super.getSource();
    }
}
