package com.chandler.fcc.common.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;

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
        return model.deepCopy();
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
            return new ObjectMapper().readTree(input);
        } catch (IOException failure) {
            throw new IllegalStateException("无法读取固定通话模型资源", failure);
        }
    }
}
