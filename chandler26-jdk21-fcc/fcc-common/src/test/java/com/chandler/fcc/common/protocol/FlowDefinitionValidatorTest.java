package com.chandler.fcc.common.protocol;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 验证管理端和运行端对固定阶段 IVR 定义使用同一严格边界。
 */
class FlowDefinitionValidatorTest {

    private static final String VALID_IVR = """
        {
          "routeMode":"IVR",
          "template":"INBOUND",
          "menu":{"enabled":true,"prompt":"/sounds/welcome.wav","timeoutSeconds":10},
          "branches":[{"digit":"1","targetType":"GROUP","target":"service","queueSeconds":120}],
          "defaultRoute":{"targetType":"AGENT","target":"901001","queueSeconds":60},
          "timeoutAction":"CALLBACK"
        }
        """;

    /**
     * 合法定义会由后端补齐不可编辑的固定阶段目录。
     */
    @Test
    void acceptsStagedIvrAndAddsFixedStages() {
        var root = FlowDefinitionValidator.validate(VALID_IVR);

        assertEquals("IVR", root.path("routeMode").asText());
        assertEquals(7, root.path("stages").size());
        assertEquals("901001", root.path("defaultRoute").path("target").asText());
    }

    /**
     * 新项目不接受 DID_DIRECT 等旧路由模型或非法 JSON。
     */
    @Test
    void rejectsLegacyAndMalformedDefinitions() {
        String[] definitions = {
            "{}",
            "[]",
            "null",
            "broken",
            "{\"routeMode\":\"DID_DIRECT\",\"didDirectConfig\":{\"workNo\":\"901001\"}}",
            "{\"routeMode\":\"RULE_ENGINE\"}"
        };

        for (String definition : definitions) {
            assertThrows(
                IllegalArgumentException.class,
                () -> FlowDefinitionValidator.validate(definition)
            );
        }
    }

    /**
     * 按键分支不能重复，防止运行时出现不确定路由。
     */
    @Test
    void rejectsDuplicateDigits() {
        String definition = VALID_IVR.replace(
            "]",
            ",{\"digit\":\"1\",\"targetType\":\"AGENT\",\"target\":\"901002\",\"queueSeconds\":60}]"
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> FlowDefinitionValidator.validate(definition)
        );
    }

    /**
     * 画布不能改写固定阶段和动作顺序。
     */
    @Test
    void rejectsChangedStageCatalog() {
        String definition = VALID_IVR.replace(
            "\"timeoutAction\":\"CALLBACK\"",
            "\"timeoutAction\":\"CALLBACK\",\"stages\":[\"ENTRY\",\"END\"]"
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> FlowDefinitionValidator.validate(definition)
        );
    }
}
