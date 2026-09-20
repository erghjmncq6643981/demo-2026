package com.chandler.learning.agent.ai.chat.application;

import com.chandler.learning.agent.common.exception.LearningErrorCode;
import com.chandler.learning.agent.exception.LearningAssistantException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiStructuredResponseRetryPolicyTest {

    @Mock
    private AiChatService aiChatService;

    private AiStructuredResponseRetryPolicy policy() {
        AiStructuredResponseRetryPolicy policy = new AiStructuredResponseRetryPolicy(aiChatService);
        ReflectionTestUtils.setField(policy, "maxAttempts", 2);
        return policy;
    }

    private static LearningAssistantException incompleteResponse() {
        return LearningAssistantException.externalService(
                LearningErrorCode.AI_RESPONSE_STRUCTURE_INCOMPLETE,
                "AI 返回内容缺少必要字段：vocabulary",
                null);
    }

    @Test
    void retriesOnceWithCorrectionWhenRequiredFieldMissing() {
        AgentChatResponse expected = new AgentChatResponse();
        when(aiChatService.chat(any())).thenThrow(incompleteResponse()).thenReturn(expected);

        List<String> usedCorrections = new ArrayList<>();
        AgentChatResponse actual = policy().execute(
                correction -> {
                    usedCorrections.add(correction);
                    return new AgentChatRequest();
                },
                failure -> "纠正要求：" + failure);

        assertThat(actual).isSameAs(expected);
        // 首次不带纠正要求，重试时携带由失败信息生成的纠正要求
        assertThat(usedCorrections).containsExactly(null, "纠正要求：AI 返回内容缺少必要字段：vocabulary");
        verify(aiChatService, times(2)).chat(any());
    }

    @Test
    void doesNotRetryUnrelatedFailures() {
        when(aiChatService.chat(any())).thenThrow(LearningAssistantException.externalService(
                LearningErrorCode.AI_MODEL_CALL_FAILED, "AI 模型调用失败", null));

        assertThatThrownBy(() -> policy().execute(
                correction -> new AgentChatRequest(),
                failure -> "纠正要求"))
                .isInstanceOf(LearningAssistantException.class)
                .hasMessageContaining("AI 模型调用失败");
        verify(aiChatService, times(1)).chat(any());
    }

    @Test
    void failsAfterAttemptsExhausted() {
        when(aiChatService.chat(any())).thenThrow(incompleteResponse());

        assertThatThrownBy(() -> policy().execute(
                correction -> new AgentChatRequest(),
                failure -> "纠正要求"))
                .isInstanceOf(LearningAssistantException.class)
                .hasFieldOrPropertyWithValue("errorCode",
                        LearningErrorCode.AI_RESPONSE_STRUCTURE_INCOMPLETE.getCode());
        verify(aiChatService, times(2)).chat(any());
    }
}
