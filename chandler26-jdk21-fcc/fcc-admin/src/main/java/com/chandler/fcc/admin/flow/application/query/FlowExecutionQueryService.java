package com.chandler.fcc.admin.flow.application.query;

import cn.dev33.satoken.stp.StpUtil;
import com.chandler.fcc.admin.flow.controller.resp.FlowExecutionInstanceResp;
import com.chandler.fcc.admin.flow.controller.resp.FlowExecutionResp;
import com.chandler.fcc.admin.flow.controller.resp.FlowExecutionStepResp;
import com.chandler.fcc.admin.flow.infrastructure.data.FlowExecutionInstanceData;
import com.chandler.fcc.admin.flow.infrastructure.data.FlowExecutionStepData;
import com.chandler.fcc.admin.flow.infrastructure.FlowStudioMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** 查询单通话固定流程快照和阶段尝试；不承担流程维护、发布或运行时通知。 */
@Service
@RequiredArgsConstructor
public class FlowExecutionQueryService {

    private final FlowStudioMapper executionMapper;

    /**
     * 读取一通电话固定的模型快照和按游标分页的动作事实。
     *
     * @param callId 业务通话标识
     * @param after 上一页末尾记录标识
     * @return 执行轨迹
     */
    public FlowExecutionResp execution(String callId, String after) {
        StpUtil.checkPermission("flow:view");
        if (!callId.matches("[1-9][0-9]{0,18}") || !after.matches("[0-9]{1,19}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "标识不合法");
        }
        if (executionMapper.callExists(callId) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "通话不存在");
        }
        var instance = executionMapper.instance(callId);
        var steps = executionMapper.steps(callId, after);
        FlowExecutionResp response = new FlowExecutionResp();
        response.setInstance(executionInstance(instance));
        response.setSteps(steps.stream().map(this::executionStep).toList());
        response.setNextCursor(steps.size() == 100 ? steps.getLast().getId() : "");
        return response;
    }

    /**
     * 将持久化投影转换为自解释的流程实例响应。
     *
     * @param data 数据库投影，无流程实例时为空
     * @return 流程实例响应
     */
    private FlowExecutionInstanceResp executionInstance(FlowExecutionInstanceData data) {
        if (data == null) {
            return FlowExecutionInstanceResp.builder().build();
        }
        return FlowExecutionInstanceResp.builder()
            .id(data.getId())
            .callId(data.getCallId())
            .versionId(data.getVersionId())
            .status(data.getStatus())
            .currentStep(data.getCurrentStep())
            .snapshot(data.getSnapshot())
            .startedAt(data.getStartedAt())
            .endedAt(data.getEndedAt())
            .build();
    }

    /**
     * 将持久化投影转换为自解释的阶段执行响应。
     *
     * @param data 阶段执行数据库投影
     * @return 阶段执行响应
     */
    private FlowExecutionStepResp executionStep(FlowExecutionStepData data) {
        return FlowExecutionStepResp.builder()
            .id(data.getId())
            .stepKey(data.getStepKey())
            .actionType(data.getActionType())
            .attemptNo(data.getAttemptNo())
            .status(data.getStatus())
            .commandId(data.getCommandId())
            .eventId(data.getEventId())
            .input(data.getInput())
            .output(data.getOutput())
            .errorCode(data.getErrorCode())
            .startedAt(data.getStartedAt())
            .endedAt(data.getEndedAt())
            .durationMs(data.getDurationMs())
            .build();
    }
}
