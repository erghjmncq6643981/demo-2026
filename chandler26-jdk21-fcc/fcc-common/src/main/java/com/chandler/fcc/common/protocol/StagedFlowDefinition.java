package com.chandler.fcc.common.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 固定阶段目录和 IVR 参数契约；不执行脚本、表达式或任意软交换命令。
 */
public final class StagedFlowDefinition {

    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * 固定动作目录，顺序也是画布布局顺序。
     */
    private static final Map<String, List<String>> STAGES = Map.of(
        "INBOUND",
        List.of("ENTRY", "MENU", "BRANCH", "ROUTE", "BRIDGE", "CONNECTED", "END"),
        "AGENT_FIRST",
        List.of("ENTRY", "DIAL_AGENT", "DIAL_CUSTOMER", "BRIDGE", "CONNECTED", "END"),
        "NOTIFICATION",
        List.of("ENTRY", "DIAL_CUSTOMER", "NOTIFY", "CONFIRM", "END")
    );

    /**
     * 工具类不允许实例化。
     */
    private StagedFlowDefinition() {}

    /**
     * 验证参数并补充只读阶段目录，目录不能由编辑者改变。
     *
     * @param root 流程定义
     * @return 带固定阶段目录的规范定义
     * @throws IllegalArgumentException 未实现动作、非法目标或参数
     */
    public static JsonNode validate(JsonNode root) {
        fields(
            root,
            Set.of("routeMode", "template", "menu", "branches", "defaultRoute", "timeoutAction", "stages")
        );
        if (!"INBOUND".equals(root.path("template").asText())) {
            throw new IllegalArgumentException("仅呼入 IVR 开放参数编排");
        }
        JsonNode menu = root.path("menu");
        fields(menu, Set.of("enabled", "prompt", "timeoutSeconds"));
        if (!menu.path("enabled").isBoolean()) {
            throw new IllegalArgumentException("必须明确是否启用菜单");
        }
        range(menu.path("timeoutSeconds"), 3, 60, "收号超时");
        if (
            menu.path("enabled").asBoolean() &&
            (!menu.path("prompt").isTextual() ||
                !menu.path("prompt").asText().matches("/[A-Za-z0-9_./-]{1,240}\\.(wav|mp3)") ||
                menu.path("prompt").asText().contains(".."))
        ) {
            throw new IllegalArgumentException("请选择软交换可读取的安全音频绝对路径");
        }
        target(root.path("defaultRoute"), false);
        if (
            !Set.of("CALLBACK", "HANGUP").contains(root.path("timeoutAction").asText())
        ) {
            throw new IllegalArgumentException("超时动作必须为回拨待办或挂机");
        }
        JsonNode branches = root.path("branches");
        if (
            !branches.isArray() ||
            branches.size() > 10 ||
            (menu.path("enabled").asBoolean() && branches.isEmpty())
        ) {
            throw new IllegalArgumentException("启用菜单时需要 1 至 10 个按键分支");
        }
        Set<String> digits = new HashSet<>();
        for (JsonNode branch : branches) {
            fields(branch, Set.of("digit", "targetType", "target", "queueSeconds"));
            String digit = branch.path("digit").asText();
            if (!digit.matches("[0-9]") || !digits.add(digit)) {
                throw new IllegalArgumentException("分支按键必须是唯一的一位数字");
            }
            target(branch, true);
        }
        ObjectNode normalized = ((ObjectNode) root).deepCopy();
        JsonNode stages = JSON.valueToTree(STAGES.get("INBOUND"));
        if (root.has("stages") && !root.get("stages").equals(stages)) {
            throw new IllegalArgumentException("阶段动作固定，不允许修改阶段目录");
        }
        normalized.set("stages", stages);
        return normalized;
    }

    /**
     * 返回系统模板快照。
     *
     * @param template 模板名称
     * @return 固定动作目录
     */
    public static JsonNode template(String template) {
        if (!STAGES.containsKey(template)) {
            throw new IllegalArgumentException("未知固定模板");
        }
        return SystemFlowModels.get(template);
    }

    /**
     * 校验一个坐席或技能组路由。
     *
     * @param node 路由配置
     * @param branch 是否为按键分支
     */
    private static void target(JsonNode node, boolean branch) {
        fields(
            node,
            branch
                ? Set.of("digit", "targetType", "target", "queueSeconds")
                : Set.of("targetType", "target", "queueSeconds")
        );
        if (
            !Set.of("AGENT", "GROUP").contains(node.path("targetType").asText()) ||
            !node.path("target").asText().matches("[A-Za-z0-9_-]{1,64}")
        ) {
            throw new IllegalArgumentException("请选择有效坐席工号或技能组代码");
        }
        range(node.path("queueSeconds"), 5, 300, "排队时限");
    }

    /**
     * 校验整数范围。
     *
     * @param node 参数值
     * @param min 下界
     * @param max 上界
     * @param label 参数名称
     */
    private static void range(JsonNode node, int min, int max, String label) {
        if (
            !node.isIntegralNumber() || !node.canConvertToInt() || node.asInt() < min || node.asInt() > max
        ) {
            throw new IllegalArgumentException(label + "超出允许范围");
        }
    }

    /**
     * 拒绝未知字段。
     *
     * @param node 对象
     * @param allowed 支持字段
     */
    private static void fields(JsonNode node, Set<String> allowed) {
        if (!node.isObject()) {
            throw new IllegalArgumentException("配置必须为对象");
        }
        node
            .fieldNames()
            .forEachRemaining(key -> {
                if (!allowed.contains(key)) {
                    throw new IllegalArgumentException("未支持字段：" + key);
                }
            });
    }
}
