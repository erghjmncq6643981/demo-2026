package com.chandler.fcc.admin.starter;

import cn.dev33.satoken.stp.StpUtil;
import com.chandler.fcc.admin.infrastructure.persistence.mapper.FlowDefinitionMapper;
import com.chandler.fcc.admin.infrastructure.persistence.mapper.FlowDefinitionVersionMapper;
import com.chandler.fcc.admin.infrastructure.persistence.entity.FlowDefinitionEntity;
import com.chandler.fcc.admin.infrastructure.persistence.entity.FlowDefinitionVersionEntity;
import com.chandler.fcc.admin.service.FlowDefinitionService;
import com.chandler.fcc.admin.model.dto.FlowPublishReq;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

/** 发布通知只能在本地事务提交后产生。 */
class FlowPublicationBoundaryTest {
    /** 提交前不发通知，提交后才发送已发布流程标识。 */
    @Test void notifiesOnlyAfterCommit() {
        var flows = mock(FlowDefinitionMapper.class);
        var versions = mock(FlowDefinitionVersionMapper.class);
        var redis = mock(StringRedisTemplate.class);
        var service = new FlowDefinitionService(flows, versions, redis);
        ReflectionTestUtils.setField(service, "fccServerBaseUrl", "");
        ReflectionTestUtils.setField(service, "reloadToken", "");
        when(flows.selectOne(any())).thenReturn(FlowDefinitionEntity.builder().id(1L).flowKey("test-flow").build());
        when(versions.selectOne(any())).thenReturn(FlowDefinitionVersionEntity.builder().id(2L).flowDefinitionId(1L)
                .versionNo(1).publishStatus("DRAFT")
                .definitionJson("{\"routeMode\":\"DID_DIRECT\",\"didDirectConfig\":{\"workNo\":\"test-agent\"}}").build());
        when(versions.selectList(any())).thenReturn(List.of());
        var request = new FlowPublishReq();
        request.setVersion("v1.0.0");
        TransactionSynchronizationManager.initSynchronization();
        try (var authentication = mockStatic(StpUtil.class)) {
            assertEquals("v1.0.0", service.publishFlow("test-flow", request));
            authentication.verify(() -> StpUtil.checkPermission("flow:write"));
            verifyNoInteractions(redis);
            TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCommit());
            verify(redis).convertAndSend("fcc:flow:publish", "test-flow");
        } finally { TransactionSynchronizationManager.clearSynchronization(); }
    }
}
