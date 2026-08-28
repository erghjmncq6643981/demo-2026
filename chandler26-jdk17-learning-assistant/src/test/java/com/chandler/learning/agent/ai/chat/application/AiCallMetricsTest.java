package com.chandler.learning.agent.ai.chat.application;

import com.chandler.learning.agent.ai.chat.domain.enums.AiInvocationScene;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiCallMetricsTest {

    @Test
    void recordsCallsLatencyAndTokenKinds() {
        var registry = new SimpleMeterRegistry();
        var metrics = new AiCallMetrics(registry);

        metrics.recordSuccess(AiInvocationScene.VOCABULARY_CARD_SINGLE,
                "deepseek", "deepseek-v4-flash", 338, 1054, 6643);

        assertThat(registry.get("learning.ai.calls").tag("result", "success").counter().count()).isEqualTo(1D);
        assertThat(registry.get("learning.ai.tokens").tag("kind", "prompt").counter().count()).isEqualTo(338D);
        assertThat(registry.get("learning.ai.tokens").tag("kind", "completion").counter().count()).isEqualTo(1054D);
        assertThat(registry.get("learning.ai.latency").timer().count()).isEqualTo(1L);
    }
}
