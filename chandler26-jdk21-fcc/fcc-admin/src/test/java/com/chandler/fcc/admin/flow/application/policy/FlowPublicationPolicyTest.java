package com.chandler.fcc.admin.flow.application.policy;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.chandler.fcc.admin.flow.infrastructure.FlowStudioMapper;
import com.chandler.fcc.admin.infrastructure.persistence.entity.FlowDefinitionEntity;
import com.chandler.fcc.admin.infrastructure.persistence.mapper.DidNumberMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

/** 验证抽取后的发布校验只做批量目标检查，不改变发布持久化。 */
class FlowPublicationPolicyTest {
    /**
     * 重复目标去重后每类只查询一次，组和坐席不混用身份。
     * @throws Exception 测试流程 JSON 解析失败
     */
    @Test
    void validatesUniqueRouteTargetsInBatches() throws Exception {
        var targets = mock(FlowStudioMapper.class);
        var policy = new FlowPublicationPolicy(mock(DidNumberMapper.class), targets);
        when(targets.activeAgentWorkNos(List.of("agent-a", "agent-b"))).thenReturn(List.of("agent-a", "agent-b"));
        when(targets.activeGroupCodes(List.of("group-a"))).thenReturn(List.of("group-a"));
        var definition = new ObjectMapper().readTree("""
            {"defaultRoute":{"targetType":"AGENT","target":"agent-a"},
             "branches":[{"targetType":"AGENT","target":"agent-a"},
             {"targetType":"AGENT","target":"agent-b"},
             {"targetType":"GROUP","target":"group-a"}]}
            """);
        policy.requireInboundRouteTargets(FlowDefinitionEntity.builder().modelType("INBOUND").build(), definition);
        verify(targets).activeAgentWorkNos(List.of("agent-a", "agent-b"));
        verify(targets).activeGroupCodes(List.of("group-a"));
        verifyNoMoreInteractions(targets);
    }

    /**
     * 不可用的组保留原业务错误，空坐席集合不发查询。
     * @throws Exception 测试流程 JSON 解析失败
     */
    @Test
    void rejectsInactiveGroupWithoutQueryingEmptyAgents() throws Exception {
        var targets = mock(FlowStudioMapper.class);
        when(targets.activeGroupCodes(List.of("disabled-group"))).thenReturn(List.of());
        var definition = new ObjectMapper().readTree("""
            {"defaultRoute":{"targetType":"GROUP","target":"disabled-group"},"branches":[]}
            """);
        var failure = assertThrows(ResponseStatusException.class, () ->
            new FlowPublicationPolicy(mock(DidNumberMapper.class), targets).requireInboundRouteTargets(
                FlowDefinitionEntity.builder().modelType("INBOUND").build(), definition));
        assertTrue(failure.getReason().contains("disabled-group"));
        verify(targets).activeGroupCodes(List.of("disabled-group"));
        verifyNoMoreInteractions(targets);
    }

    /** 非呼入模型不新增 DID 或路由校验，保持原发布适用范围。 */
    @Test
    void skipsInboundOnlyChecksForOtherModels() {
        var dids = mock(DidNumberMapper.class);
        var targets = mock(FlowStudioMapper.class);
        var policy = new FlowPublicationPolicy(dids, targets);
        var flow = FlowDefinitionEntity.builder().modelType("NOTIFICATION").build();
        policy.requireInboundDidBinding(flow);
        policy.requireInboundRouteTargets(flow, null);
        verifyNoInteractions(dids, targets);
    }
}
