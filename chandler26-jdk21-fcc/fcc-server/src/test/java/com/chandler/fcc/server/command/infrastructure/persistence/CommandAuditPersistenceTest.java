package com.chandler.fcc.server.command.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chandler.fcc.server.infrastructure.persistence.entity.CallCommandEntity;
import com.chandler.fcc.server.infrastructure.persistence.mapper.CallCommandMapper;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 命令审计职责提取后的幂等、身份关联与事务边界特征测试。 */
class CommandAuditPersistenceTest {
    /** 同一稳定 ID 和相同请求复用原意图，不再次插入。 */
    @Test
    void reusesIdenticalIntentAndRejectsDifferentSideEffects() {
        CallCommandMapper mapper = mock(CallCommandMapper.class);
        CommandAuditPersistence service = new CommandAuditPersistence(mapper);
        CallCommandEntity existing = command();
        when(mapper.selectOne(any())).thenReturn(existing);
        service.recordCommand(command());
        verify(mapper, never()).insert(any(CallCommandEntity.class));
        CallCommandEntity changed = command();
        changed.setRequestPayload("different");
        assertThrows(IllegalStateException.class, () -> service.recordCommand(changed));
    }

    /** 最终结果必须关联原控制、话道和节点，未知意图不创建副作用。 */
    @Test
    void rejectsMismatchedFinalResultAndLeavesUnknownIntentUntouched() {
        CallCommandMapper mapper = mock(CallCommandMapper.class);
        CommandAuditPersistence service = new CommandAuditPersistence(mapper);
        assertEquals(0, service.completeCommand("cmd-test", "FNode.Play", "node-a", "ctrl-a",
            "channel-a", "SUCCESS", null, null, "{}"));
        when(mapper.selectOne(any())).thenReturn(command());
        assertThrows(IllegalArgumentException.class, () -> service.completeCommand("cmd-test", "FNode.Play",
            "node-a", "other-control", "channel-a", "SUCCESS", null, null, "{}"));
        verify(mapper, never()).update(any(CallCommandEntity.class), any());
    }

    /** 意图仍在独立事务提交；重构没有把远程请求纳入该事务。 */
    @Test
    void preservesRequiresNewIntentBoundary() throws NoSuchMethodException {
        Transactional boundary = CommandAuditPersistence.class.getMethod("recordCommand", CallCommandEntity.class)
            .getAnnotation(Transactional.class);
        assertEquals(Propagation.REQUIRES_NEW, boundary.propagation());
    }

    /**
     * 构造不含秘密的标准命令审计。
     * @return 带独立控制与话道身份的请求
     */
    private CallCommandEntity command() {
        return CallCommandEntity.builder().commandId("cmd-test").methodName("FNode.Play")
            .requestPayload("{\"params\":{\"ctrl_uuid\":\"ctrl-a\",\"uuid\":\"channel-a\"}}")
            .assignedNodeId("node-a").build();
    }
}
