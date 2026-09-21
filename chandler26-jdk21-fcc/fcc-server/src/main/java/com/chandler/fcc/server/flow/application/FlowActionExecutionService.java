package com.chandler.fcc.server.flow.application;

import com.chandler.fcc.common.entity.CallInfoBO;
import com.chandler.fcc.common.enums.FlowActionExecutorType;
import com.chandler.fcc.common.enums.FlowActionType;
import com.chandler.fcc.server.flow.application.executor.FlowActionContext;
import com.chandler.fcc.server.flow.application.executor.FlowActionExecutorRegistry;
import com.chandler.fcc.server.flow.application.executor.FlowActionResult;
import com.chandler.fcc.server.flow.application.executor.FlowActionStatus;
import com.chandler.fcc.server.flow.application.executor.InternalFlowActionInvocation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * 固定运行模型调用三类动作执行器的应用入口。
 */
@Service
@RequiredArgsConstructor
public class FlowActionExecutionService {

    private final FlowActionExecutorRegistry registry;

    /**
     * 下发一个属于当前业务通话的 FNode 动作。
     *
     * @param call 当前业务通话
     * @param action 公共动作目录中的 FNode 动作
     * @param payload 与 FNode 方法匹配的公共 wire DTO
     * @param commandId 稳定命令标识
     * @return 同步受理结果
     * @throws ResponseStatusException Sidecar 明确拒绝或结果未知
     */
    public FlowActionResult executeFNode(
        CallInfoBO call,
        FlowActionType action,
        Object payload,
        String commandId
    ) {
        if (action.getExecutorType() != FlowActionExecutorType.FNODE_COMMAND) {
            throw new IllegalArgumentException("动作不是 FNode 指令: " + action);
        }
        call.putData("flowCommandId", commandId);
        FlowActionResult result = registry.execute(
            FlowActionContext.builder()
                .action(action)
                .callId(call.getCallId())
                .commandId(commandId)
                .payload(payload)
                .build()
        );
        requireAccepted(result);
        return result;
    }

    /**
     * 执行由当前业务服务显式提供的内部动作。
     *
     * @param call 当前业务通话
     * @param action 公共动作目录中的内部动作
     * @param invocation 明确业务函数
     * @return 已完成的内部动作结果
     */
    public FlowActionResult executeInternal(
        CallInfoBO call,
        FlowActionType action,
        InternalFlowActionInvocation invocation
    ) {
        if (action.getExecutorType() != FlowActionExecutorType.INTERNAL_METHOD) {
            throw new IllegalArgumentException("动作不是内部业务方法: " + action);
        }
        return registry.execute(
            FlowActionContext.builder()
                .action(action)
                .callId(call.getCallId())
                .internalInvocation(invocation)
                .build()
        );
    }

    /**
     * 调用一个服务端白名单中的第三方流程端点。
     *
     * <p>流程定义只能携带稳定端点键，不能携带 URL。同一业务动作重试时
     * 必须复用原命令标识，未知结果不得更换标识重放。</p>
     *
     * @param call 当前业务通话
     * @param action 公共目录中的第三方接口动作
     * @param endpointKey 服务端配置的端点键
     * @param payload 序列化请求载荷
     * @param commandId 稳定幂等命令标识
     * @return 第三方接口的即时结果
     */
    public FlowActionResult executeThirdParty(
        CallInfoBO call,
        FlowActionType action,
        String endpointKey,
        Object payload,
        String commandId
    ) {
        if (action.getExecutorType() != FlowActionExecutorType.THIRD_PARTY_API) {
            throw new IllegalArgumentException("动作不是第三方接口: " + action);
        }
        FlowActionResult result = registry.execute(
            FlowActionContext.builder()
                .action(action)
                .callId(call.getCallId())
                .flowInstanceId(call.getDataStr("flowInstanceId", null))
                .commandId(commandId)
                .endpointKey(endpointKey)
                .payload(payload)
                .build()
        );
        requireAccepted(result);
        return result;
    }

    /**
     * 将即时 FNode 结果转换为明确的 HTTP/业务异常。
     *
     * @param result 执行器即时结果
     */
    private void requireAccepted(FlowActionResult result) {
        if (result.getStatus() == FlowActionStatus.UNKNOWN) {
            throw new ResponseStatusException(
                HttpStatus.GATEWAY_TIMEOUT,
                "指令结果未知，请等待事件或查询原命令，不要更换命令标识重试"
            );
        }
        if (result.getStatus() != FlowActionStatus.ACCEPTED) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "软交换拒绝指令");
        }
    }
}
