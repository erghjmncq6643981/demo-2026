package com.chandler.fcc.common.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;

/**
 * 管理面与执行器共用的已实现流程定义边界，不接受未实现动作或旧字段别名。
 */
public final class FlowDefinitionValidator {

    private static final ObjectMapper MAPPER = new ObjectMapper().enable(
        com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS
    );

    /**
     * 工具类不允许实例化。
     */
    private FlowDefinitionValidator() {}

    /**
     * 校验当前支持的 DID 直达坐席定义。
     *
     * @param json 完整定义 JSON
     * @return 已校验对象
     * @throws IllegalArgumentException 定义非法或包含未支持字段
     */
    public static JsonNode validate(String json) {
        try {
            JsonNode root = MAPPER.readTree(json);
            if ("IVR".equals(root.path("routeMode").asText())) return StagedFlowDefinition.validate(root);
            requireFields(root, Set.of("routeMode", "didDirectConfig"));
            if (!"DID_DIRECT".equals(root.path("routeMode").asText())) throw new IllegalArgumentException(
                "当前仅支持 DID_DIRECT；规则引擎和 HTTP 路由尚未实现"
            );
            JsonNode target = root.path("didDirectConfig");
            requireFields(target, Set.of("workNo"));
            if (
                !target.path("workNo").isTextual() ||
                !target.path("workNo").asText().matches("[A-Za-z0-9_-]{1,64}")
            ) throw new IllegalArgumentException(
                "didDirectConfig.workNo 必须为有效坐席工号，不能使用数据库 ID"
            );
            return root;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("流程定义不是有效 JSON 对象");
        }
    }

    /**
     * 拒绝未实现字段，防止保存后被执行器静默忽略。
     *
     * @param node 待验证对象
     * @param fields 支持字段集合
     */
    private static void requireFields(JsonNode node, Set<String> fields) {
        if (node == null || !node.isObject()) throw new IllegalArgumentException("流程定义节点必须是对象");
        node
            .fieldNames()
            .forEachRemaining(name -> {
                if (!fields.contains(name)) throw new IllegalArgumentException(
                    "流程定义包含未支持字段: " + name
                );
            });
    }
}
