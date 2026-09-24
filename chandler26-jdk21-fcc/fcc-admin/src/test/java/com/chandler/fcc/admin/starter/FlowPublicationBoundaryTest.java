package com.chandler.fcc.admin.starter;

import cn.dev33.satoken.stp.StpUtil;
import com.chandler.fcc.admin.flow.application.FlowStudioService;
import com.chandler.fcc.admin.flow.controller.req.PublishFlowReq;
import com.chandler.fcc.admin.flow.infrastructure.FlowStudioMapper;
import com.chandler.fcc.admin.infrastructure.persistence.mapper.DidNumberMapper;
import com.chandler.fcc.admin.infrastructure.persistence.mapper.FlowDefinitionMapper;
import com.chandler.fcc.admin.infrastructure.persistence.mapper.FlowDefinitionVersionMapper;
import com.chandler.fcc.admin.infrastructure.persistence.entity.FlowDefinitionEntity;
import com.chandler.fcc.admin.infrastructure.persistence.entity.FlowDefinitionVersionEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.web.server.ResponseStatusException;

/** 发布通知只能在本地事务提交后产生。 */
class FlowPublicationBoundaryTest {

    private static final String VALID_IVR = """
        {"routeMode":"IVR","template":"INBOUND",
         "menu":{"enabled":false,"prompt":"","timeoutSeconds":10},"branches":[],
         "defaultRoute":{"targetType":"AGENT","target":"901001","queueSeconds":60},
         "timeoutAction":"CALLBACK"}
        """;

    /** 提交前不发通知，提交后才发送已发布流程标识。 */
    @Test void notifiesOnlyAfterCommit() {
        var flows = mock(FlowDefinitionMapper.class);
        var versions = mock(FlowDefinitionVersionMapper.class);
        var didNumbers = mock(DidNumberMapper.class);
        var redis = mock(StringRedisTemplate.class);
        var executions = mock(FlowStudioMapper.class);
        var service = new FlowStudioService(flows, versions, didNumbers, executions, redis, new ObjectMapper());
        ReflectionTestUtils.setField(service, "fccServerBaseUrl", "");
        ReflectionTestUtils.setField(service, "reloadToken", "");
        when(flows.selectOne(any())).thenReturn(FlowDefinitionEntity.builder()
            .id(1L).flowKey("test-flow").modelType("INBOUND").build());
        when(didNumbers.selectCount(any())).thenReturn(1L);
        when(executions.activeAgentWorkNos(any())).thenReturn(List.of("901001"));
        var draft = FlowDefinitionVersionEntity.builder().id(2L).flowDefinitionId(1L)
            .versionNo(1).publishStatus("DRAFT")
            .definitionJson(VALID_IVR).build();
        when(versions.selectList(any())).thenReturn(List.of(draft), List.of());
        var request = new PublishFlowReq();
        request.setVersion("v1.0.0");
        TransactionSynchronizationManager.initSynchronization();
        try (var authentication = mockStatic(StpUtil.class)) {
            assertEquals("v1.0.0", service.publish("test-flow", request).getVersion());
            authentication.verify(() -> StpUtil.checkPermission("flow:write"));
            verifyNoInteractions(redis);
            TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCommit());
            verify(redis).convertAndSend("fcc:flow:publish", "test-flow");
        } finally { TransactionSynchronizationManager.clearSynchronization(); }
    }

    /** 没有 DID 入口的呼入流程不能发布不可达版本。 */
    @Test void rejectsInboundPublicationWithoutDid() {
        var flows = mock(FlowDefinitionMapper.class);
        var versions = mock(FlowDefinitionVersionMapper.class);
        var didNumbers = mock(DidNumberMapper.class);
        var redis = mock(StringRedisTemplate.class);
        var executions = mock(FlowStudioMapper.class);
        var service = new FlowStudioService(flows, versions, didNumbers, executions, redis, new ObjectMapper());
        when(flows.selectOne(any())).thenReturn(FlowDefinitionEntity.builder()
            .id(1L).flowKey("test-flow").modelType("INBOUND").build());
        when(didNumbers.selectCount(any())).thenReturn(0L);
        var request = new PublishFlowReq();
        request.setVersion("v1.0.0");

        try (var authentication = mockStatic(StpUtil.class)) {
            assertThrows(ResponseStatusException.class, () -> service.publish("test-flow", request));
        }
        verifyNoInteractions(versions, redis);
    }

    /** 发布前必须拒绝不存在或停用的坐席路由，避免来电时才暴露配置错误。 */
    @Test void rejectsInboundPublicationWithInactiveRouteTarget() {
        var flows = mock(FlowDefinitionMapper.class);
        var versions = mock(FlowDefinitionVersionMapper.class);
        var didNumbers = mock(DidNumberMapper.class);
        var redis = mock(StringRedisTemplate.class);
        var executions = mock(FlowStudioMapper.class);
        var service = new FlowStudioService(flows, versions, didNumbers, executions, redis, new ObjectMapper());
        when(flows.selectOne(any())).thenReturn(FlowDefinitionEntity.builder()
            .id(1L).flowKey("test-flow").modelType("INBOUND").build());
        when(didNumbers.selectCount(any())).thenReturn(1L);
        when(executions.activeAgentWorkNos(any())).thenReturn(List.of());
        var draft = FlowDefinitionVersionEntity.builder().id(2L).flowDefinitionId(1L)
            .versionNo(1).publishStatus("DRAFT")
            .definitionJson(VALID_IVR).build();
        when(versions.selectList(any())).thenReturn(List.of(draft));
        var request = new PublishFlowReq();
        request.setVersion("v1.0.0");

        try (var authentication = mockStatic(StpUtil.class)) {
            ResponseStatusException failure = assertThrows(
                ResponseStatusException.class,
                () -> service.publish("test-flow", request)
            );
            assertTrue(failure.getReason().contains("901001"));
        }
        verifyNoInteractions(redis);
    }
}
