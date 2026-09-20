package com.chandler.fcc.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IdUtil 单元测试
 *
 * @author Chandler
 */
class IdUtilTest {

    @Test
    @DisplayName("测试雪花算法生成唯一且递增ID")
    void testNextId() {
        int count = 10000;
        Set<Long> idSet = new HashSet<>(count);
        long lastId = 0L;

        for (int i = 0; i < count; i++) {
            long currentId = IdUtil.nextId();
            assertTrue(currentId > 0, "ID 必须为正整数");
            assertTrue(currentId > lastId, "雪花 ID 必须单调递增");
            assertTrue(idSet.add(currentId), "雪花 ID 在单节点并发下不得重复");
            lastId = currentId;
        }
        assertEquals(count, idSet.size());
    }

    @Test
    @DisplayName("测试全局通话标识 call_id 生成规范")
    void testGetCallId() {
        String callId1 = IdUtil.getCallId();
        String callId2 = IdUtil.getCallId();

        assertNotNull(callId1);
        assertTrue(Long.parseLong(callId1) > 0, "call_id 必须可无损写入 BIGINT 关联字段");
        assertNotEquals(callId1, callId2, "连续生成的 call_id 不得重复");
    }

    @Test
    @DisplayName("测试控制流程标识 ctrl_id 生成规范")
    void testGetCtrlId() {
        String ctrlId = IdUtil.getCtrlId("fcc-inbound");
        assertNotNull(ctrlId);
        assertTrue(ctrlId.startsWith("fcc-inbound-"), "ctrl_id 必须带有指定的前缀");
    }

    @Test
    @DisplayName("测试指令编号 command_id 与事件编号 event_id 生成规范")
    void testCommandAndEventId() {
        String cmdId = IdUtil.getCommandId();
        String evtId = IdUtil.getEventId();

        assertTrue(cmdId.startsWith("cmd-"), "command_id 必须以 cmd- 前缀开头");
        assertTrue(evtId.startsWith("evt-"), "event_id 必须以 evt- 前缀开头");
    }

    @Test
    @DisplayName("测试标准 FreeSWITCH 话道 UUID")
    void testGetUuid() {
        String uuid = IdUtil.getUuid();
        assertNotNull(uuid);
        assertEquals(36, uuid.length(), "UUID 长度必须为 36 位");
        assertEquals(uuid, UUID.fromString(uuid).toString());
    }
}
