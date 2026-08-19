package com.chandler.learning.agent.support;

import com.chandler.learning.agent.domain.dto.ModelChatRequest;
import com.chandler.learning.agent.domain.enums.AiRequestAdapterType;
import com.chandler.learning.agent.exception.LearningAssistantException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 模型请求预处理适配器注册表。
 */
@Component
@RequiredArgsConstructor
public class AiModelRequestAdapterRegistry {

    private final List<AiModelRequestAdapter> adapters;

    /**
     * 使用运行时模型画像已经确定的适配器处理请求。
     */
    public AiPreparedModelRequest prepare(ModelChatRequest request) {
        AiRequestAdapterType adapterType = request.getRequestAdapter();
        return adapters.stream()
                .filter(adapter -> adapter.type() == adapterType)
                .findFirst()
                .orElseThrow(() -> LearningAssistantException.system(
                        LearningConstants.ErrorCode.SYSTEM_UNEXPECTED,
                        "模型请求适配器未注册：" + adapterType,
                        null))
                .prepare(request);
    }
}
