package com.chandler.learning.agent.ai.chat.application;

import com.chandler.learning.agent.ai.chat.domain.enums.AiInvocationScene;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/** AI 调用成本、时延和结果指标。 */
@Component
@RequiredArgsConstructor
public class AiCallMetrics {

    private final MeterRegistry meterRegistry;

    public void recordSuccess(AiInvocationScene scene, String provider, String model,
                              Integer promptTokens, Integer completionTokens, long latencyMs) {
        List<Tag> tags = tags(scene, provider, model, "success");
        meterRegistry.counter("learning.ai.calls", tags).increment();
        meterRegistry.timer("learning.ai.latency", tags).record(Duration.ofMillis(Math.max(0L, latencyMs)));
        meterRegistry.counter("learning.ai.tokens", append(tags, "kind", "prompt"))
                .increment(nonNegative(promptTokens));
        meterRegistry.counter("learning.ai.tokens", append(tags, "kind", "completion"))
                .increment(nonNegative(completionTokens));
    }

    public void recordFailure(AiInvocationScene scene, String provider, String model,
                              String errorCode, long latencyMs) {
        List<Tag> tags = append(tags(scene, provider, model, "failure"), "error_code", safe(errorCode));
        meterRegistry.counter("learning.ai.calls", tags).increment();
        meterRegistry.timer("learning.ai.latency", tags).record(Duration.ofMillis(Math.max(0L, latencyMs)));
    }

    private List<Tag> tags(AiInvocationScene scene, String provider, String model, String result) {
        return List.of(
                Tag.of("scene", scene == null ? "unknown" : scene.getCode()),
                Tag.of("provider", safe(provider)),
                Tag.of("model", safe(model)),
                Tag.of("result", result));
    }

    private List<Tag> append(List<Tag> tags, String key, String value) {
        var result = new java.util.ArrayList<>(tags);
        result.add(Tag.of(key, value));
        return List.copyOf(result);
    }

    private double nonNegative(Integer value) {
        return value == null ? 0D : Math.max(0, value);
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
