package com.chandler.fcc.admin.starter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.dev33.satoken.stp.StpUtil;
import com.chandler.fcc.admin.flow.application.FlowStudioService;
import com.chandler.fcc.admin.flow.controller.req.CreateFlowReq;
import com.chandler.fcc.admin.flow.infrastructure.FlowStudioMapper;
import com.chandler.fcc.admin.infrastructure.persistence.entity.FlowDefinitionEntity;
import com.chandler.fcc.admin.infrastructure.persistence.entity.FlowDefinitionVersionEntity;
import com.chandler.fcc.admin.infrastructure.persistence.mapper.DidNumberMapper;
import com.chandler.fcc.admin.infrastructure.persistence.mapper.FlowDefinitionMapper;
import com.chandler.fcc.admin.infrastructure.persistence.mapper.FlowDefinitionVersionMapper;
import com.chandler.fcc.common.enums.FlowTemplateType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 验证流程主数据与首个草稿必须作为一个创建用例完整落库。
 */
class FlowCreationBoundaryTest {

    /**
     * 创建呼入流程时同步持久化与类型匹配的 V1 草稿骨架。
     */
    @Test
    void createsMasterDataAndInitialDraftTogether() throws Exception {
        FlowDefinitionMapper flows = mock(FlowDefinitionMapper.class);
        FlowDefinitionVersionMapper versions = mock(FlowDefinitionVersionMapper.class);
        FlowStudioService service = new FlowStudioService(
            flows,
            versions,
            mock(DidNumberMapper.class),
            mock(FlowStudioMapper.class),
            mock(StringRedisTemplate.class),
            new ObjectMapper()
        );
        CreateFlowReq request = new CreateFlowReq();
        request.setFlowKey("SERVICE_INBOUND");
        request.setFlowName("客服呼入");
        request.setModelType(FlowTemplateType.INBOUND);

        try (var authentication = mockStatic(StpUtil.class)) {
            authentication.when(StpUtil::getLoginIdDefaultNull).thenReturn("admin");

            service.create(request);

            authentication.verify(() -> StpUtil.checkPermission("flow:write"));
        }

        ArgumentCaptor<FlowDefinitionEntity> flow = ArgumentCaptor.forClass(
            FlowDefinitionEntity.class
        );
        ArgumentCaptor<FlowDefinitionVersionEntity> draft = ArgumentCaptor.forClass(
            FlowDefinitionVersionEntity.class
        );
        verify(flows).insert(flow.capture());
        verify(versions).insert(draft.capture());
        assertEquals("INBOUND", flow.getValue().getModelType());
        assertEquals(flow.getValue().getId(), draft.getValue().getFlowDefinitionId());
        assertEquals(1, draft.getValue().getVersionNo());
        assertEquals("DRAFT", draft.getValue().getPublishStatus());
        assertEquals("admin", draft.getValue().getCreatedBy());
        assertTrue(
            new ObjectMapper()
                .readTree(draft.getValue().getDefinitionJson())
                .path("defaultRoute")
                .path("target")
                .asText()
                .isEmpty()
        );
        verify(flows).selectOne(any());
    }

    /**
     * 已发布版本派生新草稿时不改变当前发布版本，只复制定义到下一个版本号。
     */
    @Test
    void derivesDraftFromPublishedVersionWithoutChangingActiveVersion() {
        FlowDefinitionMapper flows = mock(FlowDefinitionMapper.class);
        FlowDefinitionVersionMapper versions = mock(FlowDefinitionVersionMapper.class);
        FlowStudioService service = new FlowStudioService(
            flows,
            versions,
            mock(DidNumberMapper.class),
            mock(FlowStudioMapper.class),
            mock(StringRedisTemplate.class),
            new ObjectMapper()
        );
        FlowDefinitionEntity flow = FlowDefinitionEntity.builder()
            .id(1L)
            .flowKey("SERVICE_INBOUND")
            .flowName("客服呼入")
            .modelType("INBOUND")
            .status("PUBLISHED")
            .currentVersion(1)
            .build();
        String definition = """
            {"routeMode":"IVR","template":"INBOUND",
             "menu":{"enabled":false,"prompt":"","timeoutSeconds":10},"branches":[],
             "defaultRoute":{"targetType":"AGENT","target":"901001","queueSeconds":60},
             "timeoutAction":"CALLBACK"}
            """;
        FlowDefinitionVersionEntity published = FlowDefinitionVersionEntity.builder()
            .id(2L)
            .flowDefinitionId(1L)
            .versionNo(1)
            .definitionJson(definition)
            .publishStatus("PUBLISHED")
            .createdBy("admin")
            .build();
        when(flows.selectOne(any())).thenReturn(flow);
        when(versions.selectList(any())).thenReturn(java.util.List.of());
        when(versions.selectOne(any())).thenReturn(published, published);

        FlowDefinitionVersionEntity captured;
        try (var authentication = mockStatic(StpUtil.class)) {
            authentication.when(StpUtil::getLoginIdDefaultNull).thenReturn("editor");
            assertEquals(
                "v1.1.0",
                service.createDraftFromVersion("SERVICE_INBOUND", 1).getVersion()
            );
        }

        ArgumentCaptor<FlowDefinitionVersionEntity> draft = ArgumentCaptor.forClass(
            FlowDefinitionVersionEntity.class
        );
        verify(versions).insert(draft.capture());
        captured = draft.getValue();
        assertEquals(2, captured.getVersionNo());
        assertEquals("DRAFT", captured.getPublishStatus());
        assertEquals("editor", captured.getCreatedBy());
        assertEquals(1, flow.getCurrentVersion());
        assertEquals("PUBLISHED", flow.getStatus());
        verify(versions, never()).updateById(published);
    }

    /**
     * 已有唯一草稿时编辑历史版本必须幂等返回该草稿，避免创建并行草稿。
     */
    @Test
    void reusesExistingDraftWhenEditingPublishedVersion() {
        FlowDefinitionMapper flows = mock(FlowDefinitionMapper.class);
        FlowDefinitionVersionMapper versions = mock(FlowDefinitionVersionMapper.class);
        FlowStudioService service = new FlowStudioService(
            flows,
            versions,
            mock(DidNumberMapper.class),
            mock(FlowStudioMapper.class),
            mock(StringRedisTemplate.class),
            new ObjectMapper()
        );
        when(flows.selectOne(any())).thenReturn(
            FlowDefinitionEntity.builder()
                .id(1L)
                .flowKey("SERVICE_INBOUND")
                .modelType("INBOUND")
                .build()
        );
        FlowDefinitionVersionEntity draft = FlowDefinitionVersionEntity.builder()
            .id(3L)
            .flowDefinitionId(1L)
            .versionNo(2)
            .definitionJson("{}")
            .publishStatus("DRAFT")
            .build();
        when(versions.selectList(any())).thenReturn(java.util.List.of(draft));

        try (var authentication = mockStatic(StpUtil.class)) {
            assertEquals(
                "v1.1.0",
                service.createDraftFromVersion("SERVICE_INBOUND", 1).getVersion()
            );
        }

        verify(versions, never()).insert(any(FlowDefinitionVersionEntity.class));
        verify(versions, never()).selectOne(any());
    }
}
