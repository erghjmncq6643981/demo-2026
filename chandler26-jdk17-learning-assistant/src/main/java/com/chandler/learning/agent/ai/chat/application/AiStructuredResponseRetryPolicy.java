package com.chandler.learning.agent.ai.chat.application;

import com.chandler.learning.agent.common.exception.LearningErrorCode;
import com.chandler.learning.agent.exception.LearningAssistantException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * 结构化 AI 响应的定点重试策略。
 *
 * <p>模型偶尔会返回语法合法、但缺少必需字段或提前收尾的 JSON。这类失败与业务输入无关，
 * 属于上游非确定性输出：同样的输入再次调用往往能拿到完整结构。因此本策略在保持同一业务输入
 * 的前提下追加一段“纠正要求”后重试，而不是直接把整个学习计划任务判死。</p>
 *
 * <p>重试次数有上限（默认首次 + 一次纠正重试）；一旦超出上限，仍把最后一次异常抛给调用方，
 * 由业务层决定如何提示用户。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiStructuredResponseRetryPolicy {

    private final AiChatService aiChatService;

    /** 结构化响应允许的最大尝试次数，默认 2 次。 */
    @Value("${learning.ai.structured-response.max-attempts:2}")
    private int maxAttempts;

    /**
     * 执行结构化模型调用。
     *
     * @param requestFactory   按“纠正要求”构造本次请求，入参为 null 表示首次尝试、无需纠正
     * @param correctionBuilder 依据失败信息生成追加给模型的纠正要求
     * @return 首个通过结构校验的模型响应
     */
    public AgentChatResponse execute(Function<String, AgentChatRequest> requestFactory,
                                     UnaryOperator<String> correctionBuilder) {
        int attempts = Math.max(1, maxAttempts);
        String correction = null;
        LearningAssistantException lastFailure = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                return aiChatService.chat(requestFactory.apply(correction));
            } catch (LearningAssistantException ex) {
                if (!isStructureIncomplete(ex) || attempt >= attempts) {
                    throw ex;
                }
                lastFailure = ex;
                correction = correctionBuilder.apply(ex.getMessage());
                log.warn("event=ai_structured_retry attempt={} maxAttempts={} reason={}",
                        attempt, attempts, ex.getMessage());
            }
        }
        throw lastFailure;
    }

    /** 只有“结构缺失”类失败值得重试：网络、配额、内容安全等问题重试同一输入没有意义。 */
    private boolean isStructureIncomplete(LearningAssistantException ex) {
        return LearningErrorCode.AI_RESPONSE_STRUCTURE_INCOMPLETE.getCode().equals(ex.getErrorCode());
    }
}
