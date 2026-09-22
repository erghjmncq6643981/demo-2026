package com.chandler.fcc.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.dev33.satoken.stp.StpUtil;
import com.chandler.fcc.admin.infrastructure.persistence.entity.DidNumberEntity;
import com.chandler.fcc.admin.infrastructure.persistence.entity.FlowDefinitionEntity;
import com.chandler.fcc.admin.infrastructure.persistence.mapper.DidNumberMapper;
import com.chandler.fcc.admin.infrastructure.persistence.mapper.FlowDefinitionMapper;
import com.chandler.fcc.admin.infrastructure.persistence.mapper.OutboundNumberMapper;
import com.chandler.fcc.admin.infrastructure.persistence.mapper.TelephonyNodeMapper;
import com.chandler.fcc.admin.service.TelephonyResourceService;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;

/**
 * DID 被叫号码与呼入流程绑定规则测试。
 */
class TelephonyResourceBindingTest {

    /**
     * 绑定时只接受存在的呼入流程，并将稳定 flow key 写入 DID 路由字段。
     */
    @Test
    void bindsDidToInboundFlow() {
        DidNumberMapper dids = mock(DidNumberMapper.class);
        FlowDefinitionMapper flows = mock(FlowDefinitionMapper.class);
        DidNumberEntity did = DidNumberEntity.builder().id(1L).status("ENABLED").build();
        when(dids.selectOne(any())).thenReturn(did);
        when(flows.selectOne(any())).thenReturn(
            FlowDefinitionEntity.builder().flowKey("SERVICE_INBOUND").modelType("INBOUND").build()
        );
        TelephonyResourceService service = service(dids, flows);

        try (var authentication = mockStatic(StpUtil.class)) {
            service.bindDidFlow(1L, "SERVICE_INBOUND");
            authentication.verify(() -> StpUtil.checkPermission("resource:write"));
        }

        assertEquals("SERVICE_INBOUND", did.getRouteKey());
        verify(dids).updateById(did);
    }

    /**
     * 绑定不存在或非呼入模型时必须拒绝，避免生成不可执行入口。
     */
    @Test
    void rejectsUnknownInboundFlow() {
        DidNumberMapper dids = mock(DidNumberMapper.class);
        FlowDefinitionMapper flows = mock(FlowDefinitionMapper.class);
        when(dids.selectOne(any())).thenReturn(
            DidNumberEntity.builder().id(1L).status("ENABLED").build()
        );
        when(flows.selectOne(any())).thenReturn(null);
        TelephonyResourceService service = service(dids, flows);

        try (var authentication = mockStatic(StpUtil.class)) {
            assertThrows(
                IllegalArgumentException.class,
                () -> service.bindDidFlow(1L, "OUTBOUND_FLOW")
            );
        }
    }

    /**
     * 已停用但仍有历史绑定的 DID 必须允许解绑，避免管理端展示无法清理的绑定。
     */
    @Test
    void unbindsDisabledDid() {
        DidNumberMapper dids = mock(DidNumberMapper.class);
        when(dids.selectOne(any())).thenReturn(
            DidNumberEntity.builder()
                .id(1L)
                .status("DISABLED")
                .routeKey("SERVICE_INBOUND")
                .build()
        );
        TelephonyResourceService service = service(dids, mock(FlowDefinitionMapper.class));

        try (var authentication = mockStatic(StpUtil.class)) {
            service.unbindDidFlow(1L);
            authentication.verify(() -> StpUtil.checkPermission("resource:write"));
        }

        ArgumentCaptor<DidNumberEntity> captor = ArgumentCaptor.forClass(DidNumberEntity.class);
        verify(dids).updateById(captor.capture());
        assertEquals(null, captor.getValue().getRouteKey());
    }

    /**
     * 构造仅测试 DID 绑定所需的服务依赖。
     *
     * @param dids DID Mapper
     * @param flows 流程 Mapper
     * @return 通信资源服务
     */
    private TelephonyResourceService service(DidNumberMapper dids, FlowDefinitionMapper flows) {
        return new TelephonyResourceService(
            dids,
            flows,
            mock(OutboundNumberMapper.class),
            mock(TelephonyNodeMapper.class)
        );
    }
}
