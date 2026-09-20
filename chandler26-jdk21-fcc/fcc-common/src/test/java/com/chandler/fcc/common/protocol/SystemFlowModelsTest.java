package com.chandler.fcc.common.protocol;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 验证完整模型的动作覆盖、分支引用和入口可达性，防止再次只初始化阶段目录。
 */
class SystemFlowModelsTest {

    /**
     * 每个阶段有动作定义，所有节点从入口可达，非终态必须具有合法后继。
     */
    @Test
    void everyModelHasCompleteReachableActions() {
        for (String name : List.of("INBOUND", "AGENT_FIRST", "NOTIFICATION", "PHONE_BINDING")) {
            var model = SystemFlowModels.get(name);
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
