package com.chandler.fcc.server.starter;

import com.chandler.fcc.server.flow.FlowConfig;
import com.chandler.fcc.common.enums.CallStageState;
import com.chandler.fcc.common.enums.FlowModelType;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** 运行端编译失败必须返回失败并保留此前有效定义。 */
class FlowReloadBoundaryTest {
    /** 有效定义生效后，坏定义不能清空或替换运行节点。 */
    @Test void invalidReloadKeepsPreviousDefinition() {
        var jdbc = mock(JdbcTemplate.class);
        var flow = new FlowConfig();
        ReflectionTestUtils.setField(flow, "jdbcTemplate", jdbc);
        Map<String, Object> good = Map.of("model_type", "INBOUND", "flow_name", "test-flow",
                "definition_json", "{\"routeMode\":\"DID_DIRECT\",\"didDirectConfig\":{\"workNo\":\"test-agent\"}}");
        Map<String, Object> bad = Map.of("model_type", "INBOUND", "flow_name", "bad-flow", "definition_json", "{}");
        when(jdbc.queryForList(anyString(), eq("FLOW-INBOUND"))).thenReturn(List.of(good), List.of(bad));
        assertTrue(flow.reloadFlow("FLOW-INBOUND"));
        var nodes = flow.getFlowNodes(CallStageState.ROUTE, FlowModelType.INBOUND_CUSTOMER_SERVICE.name());
        assertFalse(flow.reloadFlow("FLOW-INBOUND"));
        assertEquals(nodes, flow.getFlowNodes(CallStageState.ROUTE, FlowModelType.INBOUND_CUSTOMER_SERVICE.name()));
        assertEquals("test-flow", flow.getFlowName(FlowModelType.INBOUND_CUSTOMER_SERVICE.name()).orElseThrow());
    }
}
