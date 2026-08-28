package com.chandler.learning.agent.ai.gateway.protocol;

import com.chandler.learning.agent.ai.model.domain.enums.AiModelDefinition;
import com.chandler.learning.agent.ai.chat.domain.constant.AiContextBudgetConstants;
import com.chandler.learning.agent.common.constant.CommonConstants;
import org.springframework.stereotype.Component;

/**
 * AI 模型能力解析器。
 * <p>
 * 模型上下文长度是 Token 数而不是字节数。新调用只接受模型枚举中有明确能力定义的模型，
 * 不再根据模型名称片段猜测上下文窗口。
 */
@Component
public class AiModelCapabilityResolver {

    /**
     * 查询模型的总上下文窗口，单位为 Token。
     */
    public int contextWindowTokens(String provider, String modelName) {
        return resolve(provider, modelName).getContextWindowTokens();
    }

    /** 当前已枚举模型的有效窗口就是其官方原生上下文窗口。 */
    public int effectiveContextWindowTokens(AiModelDefinition modelDefinition) {
        return modelDefinition.getContextWindowTokens();
    }

    /**
     * 计算达到 90% 前的安全上下文窗口，单位为 Token。
     */
    public int safeContextWindowTokens(String provider, String modelName) {
        return effectiveContextWindowTokens(resolve(provider, modelName))
                * AiContextBudgetConstants.SAFE_USAGE_PERCENT / CommonConstants.PERCENT_BASE;
    }

    /**
     * 返回本次调用唯一的模型能力画像。
     */
    public AiModelDefinition resolve(String provider, String modelName) {
        return AiModelDefinition.resolve(provider, modelName);
    }
}
