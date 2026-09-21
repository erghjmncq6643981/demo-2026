package com.chandler.fcc.common.protocol;

import com.chandler.fcc.common.enums.FlowActionType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * 完整固定通话模型目录，与数据库初始化脚本共享同一份资源定义。
 */
public final class SystemFlowModels {

    private static final JsonNode MODELS = load();

    /**
     * 工具类不允许实例化。
     */
    private SystemFlowModels() {}

    /**
     * 读取独立模型快照，调用者修改不会影响目录。
     *
     * @param template 固定模板代码
     * @return 包含动作、参数、分支及终态的完整模型
     * @throws IllegalArgumentException 模板不存在
     */
    public static JsonNode get(String template) {
        JsonNode model = MODELS.get(template);
        if (model == null) {
            throw new IllegalArgumentException("未知固定通话模型：" + template);
        }
        ObjectNode copy = model.deepCopy();
        copy.path("nodes").forEach(node -> {
            FlowActionType action = FlowActionType.fromCode(node.path("action").asText());
            ObjectNode object = (ObjectNode) node;
            object.put("actionLabel", action.getDesc());
            object.put("executorType", action.getExecutorType().name());
            object.put("executorTypeLabel", action.getExecutorType().getDesc());
            object.put("operation", action.getOperation());
            object.put(
                "fNodeMethod",
                action.getFNodeMethod() == null ? "" : action.getFNodeMethod().getWireName()
            );
        });
        return copy;
    }

    /**
     * 返回固定模板声明的全部公共业务动作。
     *
     * @param template 固定模板代码
     * @return 不可变动作集合
     */
    public static Set<FlowActionType> actions(String template) {
        JsonNode nodes = get(template).path("nodes");
        Set<FlowActionType> actions = new HashSet<>();
        nodes.forEach(node -> actions.add(FlowActionType.fromCode(node.path("action").asText())));
        return Set.copyOf(actions);
    }

    /**
     * 查询固定模板中一个阶段对应的真实业务动作。
     *
     * @param template 固定模板代码
     * @param stepKey 阶段键
     * @return 公共业务动作
     * @throws IllegalArgumentException 模板或阶段不存在
     */
    public static FlowActionType action(String template, String stepKey) {
        for (JsonNode node : get(template).path("nodes")) {
            if (stepKey.equals(node.path("key").asText())) {
                return FlowActionType.fromCode(node.path("action").asText());
            }
        }
        throw new IllegalArgumentException("固定通话模型不存在阶段: " + template + "/" + stepKey);
    }

    /**
     * 返回随应用发布的全部固定模板代码。
     *
     * @return 不可变模板代码集合
     */
    public static Set<String> templateNames() {
        Set<String> names = new HashSet<>();
        MODELS.fieldNames().forEachRemaining(names::add);
        return Set.copyOf(names);
    }

    /**
     * 加载随应用发布的模型资源，缺失时阻止使用不完整模型。
     *
     * @return 模型目录
     * @throws IllegalStateException 资源缺失或内容损坏
     */
    private static JsonNode load() {
        try (var input = SystemFlowModels.class.getResourceAsStream("/flows/system-models.json")) {
            if (input == null) {
                throw new IllegalStateException("缺少固定通话模型资源");
            }
            JsonNode models = new ObjectMapper().readTree(input);
            models.forEach(model -> {
                JsonNode nodes = model.path("nodes");
                if (!nodes.isArray() || nodes.isEmpty()) {
                    throw new IllegalStateException("固定通话模型缺少动作节点");
                }
                nodes.forEach(node -> FlowActionType.fromCode(node.path("action").asText()));
            });
            return models;
        } catch (IOException failure) {
            throw new IllegalStateException("无法读取固定通话模型资源", failure);
        }
    }
}
