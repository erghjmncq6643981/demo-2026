package com.chandler.fcc.common.protocol;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 验证管理端和运行端对未知路由、旧字段、非法定义一致拒绝。 */
class FlowDefinitionValidatorTest {
    /** 当前已实现直达模型保留工号字符串语义。 */
    @Test void acceptsSupportedWorkNumber() {
        var root = FlowDefinitionValidator.validate("{\"routeMode\":\"DID_DIRECT\",\"didDirectConfig\":{\"workNo\":\"agent-test\"}}");
        assertEquals("agent-test", root.path("didDirectConfig").path("workNo").asText());
    }
    /** 未实现能力、旧 agentId 和被静默忽略的字段均不能发布。 */
    @Test void rejectsUnsupportedDefinitions() {
        for (String definition : new String[]{"{}", "[]", "null", "broken", "{\"routeMode\":\"RULE_ENGINE\"}",
                "{\"routeMode\":\"DID_DIRECT\",\"didDirectConfig\":{\"agentId\":\"test\"}}",
                "{\"routeMode\":\"DID_DIRECT\",\"didDirectConfig\":{\"workNo\":\"test\"},\"steps\":[]}"}) {
            assertThrows(IllegalArgumentException.class, () -> FlowDefinitionValidator.validate(definition));
        }
    }
}
