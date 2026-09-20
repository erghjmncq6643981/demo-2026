package com.chandler.fcc.common.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.DeserializationFeature;
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
     * 校验当前支持的固定阶段 IVR 定义。
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
            if (!"IVR".equals(root.path("routeMode").asText())) {
                throw new IllegalArgumentException("新流程只支持固定阶段 IVR 模型");
            }
            return StagedFlowDefinition.validate(root);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("流程定义不是有效 JSON 对象");
        }
    }
}
