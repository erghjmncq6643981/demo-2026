package com.chandler.fcc.server.starter;

import com.chandler.fcc.common.entity.FlowNode;
import com.chandler.fcc.server.flow.action.executor.DialGuestActionExecutor;
import com.chandler.fcc.server.flow.action.executor.PlayActionExecutor;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证动作在缺失独立身份或号码时拒绝执行，不能向默认目标发命令。 */
class FlowActionIdentityTest {
    /** 业务 callId 和旧字段均不能替代真实 channelUuid。 */
    @Test
    void playRejectsMissingChannelEvenWithBusinessCallId() {
        FlowNode node = FlowNode.builder().data(Map.<String, Object>of(
                "ctrlId", "control-test", "uuidA", "old-channel")).build();
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new PlayActionExecutor().execute("business-call", "flow", node));
        assertTrue(error.getMessage().contains("channelUuid"));
    }

    /** 真实话道不代表存在控制身份，缺 ctrlId 必须拒绝。 */
    @Test
    void playRejectsMissingControlId() {
        FlowNode node = FlowNode.builder().data(Map.<String, Object>of(
                "channelUuid", "channel-test", "ctrlUuid", "old-control")).build();
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new PlayActionExecutor().execute("business-call", "flow", node));
        assertTrue(error.getMessage().contains("ctrlId"));
    }

    /** 未提供客户目标时不得回落到演示分机。 */
    @Test
    void dialRejectsMissingDestinationBeforeSessionMutation() {
        FlowNode node = FlowNode.builder().data(Map.<String, Object>of(
                "ctrlId", "control-test", "callerNumber", "configured-caller")).build();
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new DialGuestActionExecutor(null).execute("business-call", "flow", node));
        assertTrue(error.getMessage().contains("destNumber"));
    }
}
