package com.chandler.fcc.admin.flow.application.query;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import cn.dev33.satoken.stp.StpUtil;
import com.chandler.fcc.admin.flow.infrastructure.FlowStudioMapper;
import com.chandler.fcc.admin.flow.infrastructure.data.FlowExecutionInstanceData;
import com.chandler.fcc.admin.flow.infrastructure.data.FlowExecutionStepData;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** 验证轨迹查询拆分后保留权限、字符串身份、分页游标与事实字段，不依赖外部服务。 */
class FlowExecutionQueryServiceTest {

    /** 未授权查询必须在任何持久化访问前被拒绝。 */
    @Test
    void deniesUnauthorizedBeforeReadingFacts() {
        FlowStudioMapper mapper = mock(FlowStudioMapper.class);
        try (var authentication = mockStatic(StpUtil.class)) {
            authentication.when(() -> StpUtil.checkPermission("flow:view"))
                .thenThrow(new IllegalStateException("permission denied"));
            assertThrows(IllegalStateException.class,
                () -> new FlowExecutionQueryService(mapper).execution("9001", "0"));
            verifyNoInteractions(mapper);
        }
    }

    /** 非法通话与游标保持原校验规则，不触发数据库查询。 */
    @Test
    void rejectsInvalidIdentifiers() {
        FlowStudioMapper mapper = mock(FlowStudioMapper.class);
        try (var authentication = mockStatic(StpUtil.class)) {
            var service = new FlowExecutionQueryService(mapper);
            assertEquals(HttpStatus.BAD_REQUEST,
                assertThrows(ResponseStatusException.class, () -> service.execution("0", "0")).getStatusCode());
            assertEquals(HttpStatus.BAD_REQUEST,
                assertThrows(ResponseStatusException.class, () -> service.execution("9001", "-1")).getStatusCode());
            verifyNoInteractions(mapper);
        }
    }

    /** 不存在的通话返回明确不存在，不继续装配快照或阶段。 */
    @Test
    void rejectsUnknownCall() {
        FlowStudioMapper mapper = mock(FlowStudioMapper.class);
        try (var authentication = mockStatic(StpUtil.class)) {
            assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> new FlowExecutionQueryService(mapper).execution("9001", "0")).getStatusCode());
            verify(mapper).callExists("9001");
            verifyNoMoreInteractions(mapper);
        }
    }

    /** 通话存在但没有流程实例时，返回空快照和空列表，不制造执行成功。 */
    @Test
    void returnsEmptyExecutionWhenNoFlowRan() {
        FlowStudioMapper mapper = mock(FlowStudioMapper.class);
        when(mapper.callExists("9001")).thenReturn(1);
        when(mapper.steps("9001", "0")).thenReturn(List.of());
        try (var authentication = mockStatic(StpUtil.class)) {
            var result = new FlowExecutionQueryService(mapper).execution("9001", "0");
            authentication.verify(() -> StpUtil.checkPermission("flow:view"));
            assertNotNull(result.getInstance());
            assertNull(result.getInstance().getStatus());
            assertTrue(result.getSteps().isEmpty());
            assertEquals("", result.getNextCursor());
        }
    }

    /** 大于 JavaScript 安全整数的身份透传；阶段尝试与原始事实完整保留。 */
    @Test
    void preservesOpaqueIdentityAndFullPageCursor() {
        String callId = "9007199254740993";
        String cursor = "9007199254740994";
        FlowStudioMapper mapper = mock(FlowStudioMapper.class);
        when(mapper.callExists(callId)).thenReturn(1);
        FlowExecutionInstanceData instance = new FlowExecutionInstanceData();
        instance.setId(callId);
        instance.setCallId(callId);
        instance.setVersionId(cursor);
        instance.setSnapshot("{\"pinned\":true}");
        instance.setStatus("RUNNING");
        when(mapper.instance(callId)).thenReturn(instance);
        List<FlowExecutionStepData> steps = IntStream.range(0, 100).mapToObj(index -> {
            FlowExecutionStepData step = new FlowExecutionStepData();
            step.setId(Long.toString(9007199254741000L + index));
            step.setStepKey("dial");
            step.setAttemptNo(2);
            step.setActionType("DIAL_CUSTOMER");
            step.setCommandId("command-fact");
            step.setEventId("event-fact");
            step.setStatus("FAILED");
            step.setErrorCode("NO_RESPONDER");
            step.setInput("input-fact");
            step.setOutput("output-fact");
            step.setStartedAt(LocalDateTime.of(2026, 10, 4, 0, 0));
            step.setEndedAt(LocalDateTime.of(2026, 10, 4, 0, 1));
            step.setDurationMs(60000L);
            return step;
        }).toList();
        when(mapper.steps(callId, cursor)).thenReturn(steps);
        try (var authentication = mockStatic(StpUtil.class)) {
            var result = new FlowExecutionQueryService(mapper).execution(callId, cursor);
            assertEquals(callId, result.getInstance().getCallId());
            assertEquals(cursor, result.getInstance().getVersionId());
            assertEquals(instance.getSnapshot(), result.getInstance().getSnapshot());
            assertEquals(steps.getLast().getId(), result.getNextCursor());
            var step = result.getSteps().getFirst();
            assertEquals("FAILED", step.getStatus());
            assertEquals("NO_RESPONDER", step.getErrorCode());
            assertEquals("command-fact", step.getCommandId());
            assertEquals("event-fact", step.getEventId());
            assertEquals(2, step.getAttemptNo());
            assertEquals("input-fact", step.getInput());
            assertEquals("output-fact", step.getOutput());
            assertEquals(60000L, step.getDurationMs());
            assertEquals(steps.getFirst().getStartedAt(), step.getStartedAt());
            assertEquals(steps.getFirst().getEndedAt(), step.getEndedAt());
        }
    }
}
