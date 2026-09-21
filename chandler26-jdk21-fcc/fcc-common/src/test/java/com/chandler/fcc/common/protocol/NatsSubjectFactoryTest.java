package com.chandler.fcc.common.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * 验证 FCC 业务命令与 Sidecar 内部节点命令使用不同的主题边界。
 */
class NatsSubjectFactoryTest {

    /**
     * 业务控制面必须只使用无节点参数的逻辑分发主题。
     */
    @Test
    void exposesNodeAgnosticDispatchSubject() {
        assertEquals("fs.cmd.dispatch", NatsSubjectFactory.commandDispatch());
    }

    /**
     * 内部节点主题仍保留给 Coordinator 到 Sidecar worker 的转发。
     */
    @Test
    void keepsValidatedInternalNodeSubject() {
        assertEquals("fs.cmd.node-a", NatsSubjectFactory.command("node-a"));
    }
}
