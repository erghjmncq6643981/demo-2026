package com.chandler.fcc.common.protocol;

import com.chandler.fcc.common.enums.FlowTemplateType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 固定阶段业务流程参数契约；不执行脚本、表达式或任意软交换命令。
 */
public final class StagedFlowDefinition {

    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * 固定动作目录，顺序也是画布布局顺序。
     */
    private static final Map<String, List<String>> STAGES = Map.of(
        "INBOUND",
        List.of(
            "ENTRY",
            "MENU",
            "BRANCH",
            "ROUTE",
            "BRIDGE",
            "RECORD_START",
            "CONNECTED",
            "RECORD_STOP",
            "RATING",
            "RATING_SAVE",
            "CLOSING",
            "END"
        )
    );

    /**
     * 工具类不允许实例化。
     */
    private StagedFlowDefinition() {}

    /**
     * 创建指定业务类型的首个可维护草稿骨架。
     *
     * <p>骨架不填充虚构的坐席、技能组或通知文案，因此在补齐业务参数前不满足发布条件。</p>
     *
     * @param template 创建后不可变的流程业务类型
     * @return 包含固定阶段目录的独立草稿定义
     */
    public static JsonNode initialDraft(FlowTemplateType template) {
        ObjectNode root = JSON.createObjectNode();
        root.put("template", template.name());
        root.set("stages", JSON.valueToTree(STAGES.get(template.name())));
        root.put("routeMode", "IVR");
        ObjectNode menu = root.putObject("menu");
        menu.put("enabled", false);
        menu.put("prompt", "");
        menu.put("timeoutSeconds", 10);
        root.putArray("branches");
        ObjectNode defaultRoute = root.putObject("defaultRoute");
        defaultRoute.put("targetType", "GROUP");
        defaultRoute.put("target", "");
        defaultRoute.put("queueSeconds", 120);
        root.put("timeoutAction", "CALLBACK");
        return root;
    }

    /**
     * 验证参数并补充只读阶段目录，目录不能由编辑者改变。
     *
     * @param root 流程定义
     * @return 带固定阶段目录的规范定义
     * @throws IllegalArgumentException 未实现动作、非法目标或参数
     */
    public static JsonNode validate(JsonNode root) {
        return normalize(root, true);
    }

    /**
     * 规范化可中途保存的草稿结构，不要求业务参数已经满足发布条件。
     *
     * @param root 草稿定义
     * @return 带固定阶段目录的规范草稿
     * @throws IllegalArgumentException 类型、字段或基础参数结构不合法
     */
    public static JsonNode normalizeDraft(JsonNode root) {
        return normalize(root, false);
    }

    /**
     * 按业务类型执行草稿或发布级校验。
     *
     * @param root 流程定义
     * @param complete 是否要求满足发布级完整性
     * @return 规范化定义
     */
    private static JsonNode normalize(JsonNode root, boolean complete) {
        String template = root.path("template").asText();
        if (!FlowTemplateType.INBOUND.name().equals(template)) {
            throw new IllegalArgumentException("暂不支持该流程类型: " + template);
        }
        return validateInbound(root, complete);
    }

    /**
     * 校验呼入 IVR 参数并补齐固定阶段。
     *
     * @param root 呼入定义
     * @param complete 是否要求满足发布级完整性
     * @return 规范化呼入定义
     */
    private static JsonNode validateInbound(JsonNode root, boolean complete) {
        fields(
            root,
            Set.of("routeMode", "template", "menu", "branches", "defaultRoute", "timeoutAction", "stages")
        );
        if (!"IVR".equals(root.path("routeMode").asText())) {
            throw new IllegalArgumentException("呼入流程必须使用 IVR 模型");
        }
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
            complete &&
            menu.path("enabled").asBoolean() &&
            !validPrompt(menu.path("prompt"))
        ) {
            throw new IllegalArgumentException("导航语音必须是文案或安全音频绝对路径");
        }
        if (!complete && !validOptionalPrompt(menu.path("prompt"))) {
            throw new IllegalArgumentException("导航语音必须是文案或安全音频绝对路径");
        }
        target(root.path("defaultRoute"), false, complete);
        if (
            !Set.of("CALLBACK", "HANGUP").contains(root.path("timeoutAction").asText())
        ) {
            throw new IllegalArgumentException("超时动作必须为回拨待办或挂机");
        }
        JsonNode branches = root.path("branches");
        if (
            !branches.isArray() ||
            branches.size() > 10 ||
            (complete && menu.path("enabled").asBoolean() && branches.isEmpty())
        ) {
            throw new IllegalArgumentException("启用菜单时需要 1 至 10 个按键分支");
        }
        Set<String> digits = new HashSet<>();
        for (JsonNode branch : branches) {
            fields(branch, Set.of("digit", "targetType", "target", "queueSeconds"));
            String digit = branch.path("digit").asText();
            if (
                (complete && !digit.matches("[0-9]")) ||
                (!digit.isEmpty() && (!digit.matches("[0-9]") || !digits.add(digit)))
            ) {
                throw new IllegalArgumentException("分支按键必须是唯一的一位数字");
            }
            target(branch, true, complete);
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
     * @param complete 是否要求目标已经填写
     */
    private static void target(JsonNode node, boolean branch, boolean complete) {
        fields(
            node,
            branch
                ? Set.of("digit", "targetType", "target", "queueSeconds")
                : Set.of("targetType", "target", "queueSeconds")
        );
        String target = node.path("target").asText();
        boolean targetValid = complete
            ? target.matches("[A-Za-z0-9_-]{1,64}")
            : target.isEmpty() || target.matches("[A-Za-z0-9_-]{1,64}");
        if (!Set.of("AGENT", "GROUP").contains(node.path("targetType").asText()) || !targetValid) {
            throw new IllegalArgumentException("请选择有效坐席工号或技能组代码");
        }
        range(node.path("queueSeconds"), 5, 300, "排队时限");
    }

    /**
     * 校验导航提示。普通文本由 Sidecar TTS 合成；绝对音频路径用于预置提示音。
     *
     * @param prompt 导航提示
     * @return 是否可执行
     */
    private static boolean validPrompt(JsonNode prompt) {
        if (!prompt.isTextual()) {
            return false;
        }
        String value = prompt.asText().trim();
        if (value.isEmpty() || value.length() > 1000) {
            return false;
        }
        if (value.startsWith("/")) {
            return value.matches("/[A-Za-z0-9_./-]{1,240}\\.(wav|mp3)") && !value.contains("..");
        }
        return !value.contains("\u0000");
    }

    /**
     * 校验草稿中的可选提示内容；空值表示尚未配置。
     *
     * @param prompt 草稿提示
     * @return 空值或合法提示时返回 {@code true}
     */
    private static boolean validOptionalPrompt(JsonNode prompt) {
        return prompt.isTextual() && (prompt.asText().isBlank() || validPrompt(prompt));
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
