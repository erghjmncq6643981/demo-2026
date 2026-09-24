package com.chandler.fcc.server.flow.application.executor;

import com.chandler.fcc.common.dto.command.FNodeAnswerDTO;
import com.chandler.fcc.common.dto.command.FNodeBridgeDTO;
import com.chandler.fcc.common.dto.command.FNodeCommandResultDTO;
import com.chandler.fcc.common.dto.command.FNodeDialDTO;
import com.chandler.fcc.common.dto.command.FNodeHangupDTO;
import com.chandler.fcc.common.dto.command.FNodeNativeApiDTO;
import com.chandler.fcc.common.dto.command.FNodePlayDTO;
import com.chandler.fcc.common.dto.command.FNodeReadDTMFDTO;
import com.chandler.fcc.common.dto.command.FNodeRecordDTO;
import com.chandler.fcc.common.dto.command.FNodeTransferDTO;
import com.chandler.fcc.common.entity.FNodeResult;
import com.chandler.fcc.common.enums.FlowActionExecutorType;
import com.chandler.fcc.common.protocol.FNodeMethod;
import com.chandler.fcc.server.command.FccClient;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 使用规范 FNode 方法和公共 wire DTO 下发 Sidecar 指令。
 */
@Component
@RequiredArgsConstructor
public class FNodeFlowActionExecutor implements FlowActionExecutor {

    private static final Map<FNodeMethod, Class<?>> PARAMETER_TYPES = Map.ofEntries(
        Map.entry(FNodeMethod.DIAL, FNodeDialDTO.class),
        Map.entry(FNodeMethod.ANSWER, FNodeAnswerDTO.class),
        Map.entry(FNodeMethod.CHANNEL_BRIDGE, FNodeBridgeDTO.class),
        Map.entry(FNodeMethod.READ_DTMF, FNodeReadDTMFDTO.class),
        Map.entry(FNodeMethod.PLAY, FNodePlayDTO.class),
        Map.entry(FNodeMethod.RECORD, FNodeRecordDTO.class),
        Map.entry(FNodeMethod.HANGUP, FNodeHangupDTO.class),
        Map.entry(FNodeMethod.TRANSFER, FNodeTransferDTO.class),
        Map.entry(FNodeMethod.NATIVE_API, FNodeNativeApiDTO.class),
        Map.entry(FNodeMethod.COMMAND_RESULT, FNodeCommandResultDTO.class)
    );

    private final FccClient client;

    /**
     * 返回 FNode 指令执行器类型。
     *
     * @return FNode 指令类型
     */
    @Override
    public FlowActionExecutorType type() {
        return FlowActionExecutorType.FNODE_COMMAND;
    }

    /**
     * 校验动作映射和参数 DTO 后下发规范 FNode 指令。
     *
     * @param context 动作上下文
     * @return 仅表示同步受理、失败或未知的结果
     */
    @Override
    public FlowActionResult execute(FlowActionContext context) {
        FNodeMethod method = context.getAction().getFNodeMethod();
        if (method == null) {
            throw new IllegalArgumentException("FNode 动作未声明对应的方法: " + context.getAction());
        }
        validate(context, method);
        FNodeResult result = client.execute(
            method,
            context.getPayload(),
            context.getCommandId()
        );
        FlowActionStatus status = result == null || Integer.valueOf(-32000).equals(result.getCode())
            ? FlowActionStatus.UNKNOWN
            : result.isSuccess() ? FlowActionStatus.ACCEPTED : FlowActionStatus.FAILED;
        return FlowActionResult.builder()
            .status(status)
            .commandId(context.getCommandId())
            .code(result == null || result.getCode() == null ? null : result.getCode().toString())
            .message(result == null ? "Sidecar 未返回结果" : result.getMessage())
            .output(result == null ? null : result.getData())
            .build();
    }

    /**
     * 校验 FNode 方法是否允许无参数，或参数是否为该方法唯一 wire DTO。
     *
     * @param context 动作上下文
     * @param method 规范 FNode 方法
     */
    private void validate(FlowActionContext context, FNodeMethod method) {
        if (context.getCommandId() == null || context.getCommandId().isBlank()) {
            throw new IllegalArgumentException("FNode 动作缺少稳定命令标识");
        }
        Class<?> parameterType = PARAMETER_TYPES.get(method);
        if (parameterType == null) {
            if (context.getPayload() != null) {
                throw new IllegalArgumentException(method.getWireName() + " 不接受参数对象");
            }
            return;
        }
        if (!parameterType.isInstance(context.getPayload())) {
            throw new IllegalArgumentException(
                method.getWireName() + " 必须使用 " + parameterType.getSimpleName()
            );
        }
    }
}
