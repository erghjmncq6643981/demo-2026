package com.chandler.fcc.common.protocol;

import com.chandler.fcc.common.enums.FlowTemplateType;
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
        assertEquals(12, root.path("stages").size());
        assertEquals("901001", root.path("defaultRoute").path("target").asText());
    }

    /**
     * 普通导航文案交给 Sidecar TTS，仍使用同一流程定义校验边界。
     */
    @Test
    void acceptsTextPromptForSidecarTts() {
        String definition = VALID_IVR.replace("/sounds/welcome.wav", "您好，请按 1 转人工");

        assertEquals(
            "您好，请按 1 转人工",
            FlowDefinitionValidator.validate(definition).path("menu").path("prompt").asText()
        );
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

    /**
     * 主数据类型和版本模板不一致时必须拒绝，避免运行端错配执行器。
     */
    @Test
    void rejectsDefinitionThatDoesNotMatchMasterType() {
        assertThrows(
            IllegalArgumentException.class,
            () -> FlowDefinitionValidator.validate(
                VALID_IVR.replace("\"template\":\"INBOUND\"", "\"template\":\"NOTIFICATION\""),
                "INBOUND"
            )
        );
    }

    /**
     * 首个呼入草稿允许空路由目标中途保存，但不能通过发布级校验。
     */
    @Test
    void permitsIncompleteInboundDraftButRejectsPublication() {
        String draft = StagedFlowDefinition.initialDraft(FlowTemplateType.INBOUND).toString();

        var normalized = FlowDefinitionValidator.normalizeDraft(draft, "INBOUND");

        assertEquals("", normalized.path("defaultRoute").path("target").asText());
        assertThrows(
            IllegalArgumentException.class,
            () -> FlowDefinitionValidator.validate(draft, "INBOUND")
        );
    }

}
