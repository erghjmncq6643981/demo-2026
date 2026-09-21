package com.chandler.fcc.common.protocol;

import static org.junit.jupiter.api.Assertions.*;

import com.chandler.fcc.common.enums.FlowActionType;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 验证完整模型的动作覆盖、分支引用和入口可达性，防止再次只初始化阶段目录。
 */
class SystemFlowModelsTest {

    /**
     * 每一个标准 FNode 方法都必须能够从公共动作目录反查，避免新增命令后运行端没有动作语义。
     */
    @Test
    void everyFNodeMethodHasFlowActionType() {
        for (FNodeMethod method : FNodeMethod.values()) {
            assertNotNull(FlowActionType.fromFNodeMethod(method), method.getWireName());
        }
    }

    /**
     * 每个阶段有动作定义，所有节点从入口可达，非终态必须具有合法后继。
     */
    @Test
    void everyModelHasCompleteReachableActions() {
        for (String name : List.of("INBOUND", "AGENT_FIRST", "NOTIFICATION", "PHONE_BINDING")) {
            var model = SystemFlowModels.get(name);
            model.path("nodes").forEach(node ->
                assertFalse(node.path("inputs").toString().contains("\"nodeId\""))
            );
            var keys = new HashSet<String>();
            model.path("nodes").forEach(node -> assertTrue(keys.add(node.path("key").asText())));
            assertEquals(model.path("stages").size(), keys.size());
            model.path("stages").forEach(stage -> assertTrue(keys.contains(stage.asText())));
            var visited = new HashSet<String>();
            visited.add(model.path("entry").asText());
            for (int pass = 0; pass < keys.size(); pass++) {
                for (var node : model.path("nodes")) {
                    assertFalse(node.path("action").asText().isBlank());
                    assertFalse(node.path("actionLabel").asText().isBlank());
                    assertFalse(node.path("executorType").asText().isBlank());
                    assertFalse(node.path("executorTypeLabel").asText().isBlank());
                    assertFalse(node.path("operation").asText().isBlank());
                    assertEquals(node.path("terminal").asBoolean(), node.path("transitions").isEmpty());
                    for (var edge : node.path("transitions")) {
                        assertTrue(keys.contains(edge.path("to").asText()));
                        assertFalse(edge.path("when").asText().isBlank());
                        if (visited.contains(node.path("key").asText())) visited.add(
                            edge.path("to").asText()
                        );
                    }
                }
            }
            assertEquals(keys, visited, name);
        }
    }
}
