package com.chandler.fcc.common.protocol;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 管理面与执行器共用的已实现流程定义边界，不接受未实现动作或旧字段别名。
 */
public final class FlowDefinitionValidator {

    private static final ObjectMapper MAPPER = new ObjectMapper().enable(
        DeserializationFeature.FAIL_ON_TRAILING_TOKENS
    );

    /**
     * 工具类不允许实例化。
     */
    private FlowDefinitionValidator() {}

    /**
     * 校验当前支持的固定阶段业务流程定义。
     *
     * @param json 完整定义 JSON
     * @return 已校验对象
     * @throws IllegalArgumentException 定义非法或包含未支持字段
     */
    public static JsonNode validate(String json) {
        try {
            JsonNode root = MAPPER.readTree(json);
            if (root == null || !root.isObject()) {
                throw new IllegalArgumentException("流程定义必须是 JSON 对象");
            }
            return StagedFlowDefinition.validate(root);
        } catch (IllegalArgumentException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new IllegalArgumentException("流程定义不是有效 JSON 对象");
        }
    }

    /**
     * 校验定义属于指定的流程类型，防止主数据类型与运行快照错配。
     *
     * @param json 完整定义 JSON
     * @param expectedTemplate 流程主数据中的类型代码
     * @return 已校验并规范化的定义
     * @throws IllegalArgumentException 定义非法或类型不一致
     */
    public static JsonNode validate(String json, String expectedTemplate) {
        JsonNode normalized = validate(json);
        requireTemplate(normalized, expectedTemplate);
        return normalized;
    }

    /**
     * 规范化允许尚未补齐业务参数的草稿定义。
     *
     * <p>草稿仍必须使用受支持的类型、字段和固定阶段目录，但允许路由目标、分支按键和通知文案
     * 暂时为空；完整业务约束在显式校验和发布时执行。</p>
     *
     * @param json 完整草稿 JSON
     * @param expectedTemplate 流程主数据中的类型代码
     * @return 已规范化的草稿定义
     * @throws IllegalArgumentException JSON、结构或流程类型不合法
     */
    public static JsonNode normalizeDraft(String json, String expectedTemplate) {
        try {
            JsonNode root = MAPPER.readTree(json);
            if (root == null || !root.isObject()) {
                throw new IllegalArgumentException("流程定义必须是 JSON 对象");
            }
            JsonNode normalized = StagedFlowDefinition.normalizeDraft(root);
            requireTemplate(normalized, expectedTemplate);
            return normalized;
        } catch (IllegalArgumentException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new IllegalArgumentException("流程定义不是有效 JSON 对象");
        }
    }

    /**
     * 确认定义类型与不可变流程主数据一致。
     *
     * @param definition 已规范化定义
     * @param expectedTemplate 流程主数据类型
     * @throws IllegalArgumentException 类型不一致
     */
    private static void requireTemplate(JsonNode definition, String expectedTemplate) {
        if (!definition.path("template").asText().equalsIgnoreCase(expectedTemplate)) {
            throw new IllegalArgumentException("流程定义类型与创建时选择的类型不一致");
        }
    }
}
