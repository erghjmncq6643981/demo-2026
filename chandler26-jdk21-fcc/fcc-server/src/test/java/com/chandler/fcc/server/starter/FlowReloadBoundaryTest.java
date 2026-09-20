package com.chandler.fcc.server.starter;

import com.chandler.fcc.server.flow.FlowConfig;
import com.chandler.fcc.common.enums.FlowModelType;
import com.chandler.fcc.server.flow.infrastructure.FlowConfigMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** 运行端编译失败必须返回失败并保留此前有效定义。 */
class FlowReloadBoundaryTest {

    private static final String VALID_IVR = """
        {"routeMode":"IVR","template":"INBOUND",
         "menu":{"enabled":false,"prompt":"","timeoutSeconds":10},"branches":[],
         "defaultRoute":{"targetType":"AGENT","target":"901001","queueSeconds":60},
         "timeoutAction":"CALLBACK"}
        """;

    /** 有效定义生效后，坏定义不能清空或替换运行元数据。 */
    @Test void invalidReloadKeepsPreviousDefinition() {
        var mapper = mock(FlowConfigMapper.class);
        var flow = new FlowConfig(mapper, new ObjectMapper());
        Map<String, Object> good = Map.of(
            "flowKey", "FLOW-INBOUND",
            "modelType", "INBOUND",
            "flowName", "test-flow",
            "definitionJson", VALID_IVR
        );
        Map<String, Object> bad = Map.of(
            "flowKey", "FLOW-INBOUND",
            "modelType", "INBOUND",
            "flowName", "bad-flow",
            "definitionJson", "{}"
        );
        when(mapper.findPublishedByKey("FLOW-INBOUND")).thenReturn(good, bad);
        assertTrue(flow.reloadFlow("FLOW-INBOUND"));
        assertFalse(flow.reloadFlow("FLOW-INBOUND"));
        assertEquals("test-flow", flow.getFlowName(FlowModelType.INBOUND_CUSTOMER_SERVICE.name()).orElseThrow());
    }
}
