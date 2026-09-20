package com.chandler.learning.agent.learning.application;

import com.chandler.learning.agent.ai.chat.application.AgentChatRequest;
import com.chandler.learning.agent.ai.chat.application.AgentChatResponse;
import com.chandler.learning.agent.ai.chat.application.AiStructuredResponseRetryPolicy;
import com.chandler.learning.agent.learning.domain.entity.LearningPlan;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LearningPlanSceneContentServiceRetryTest {

    @Mock
    private AiStructuredResponseRetryPolicy structuredRetryPolicy;

    private LearningPlanSceneContentService service() {
        return new LearningPlanSceneContentService(structuredRetryPolicy, new ObjectMapper());
    }

    @Test
    void appendsRetryCorrectionToUserMessageWithoutChangingBusinessInput() {
        LearningPlan plan = new LearningPlan();
        plan.setId(1L);
        plan.setUserId(2L);
        plan.setAiSessionId(3L);
        plan.setName("四级词汇冲刺");
        plan.setLearningPurpose("备考四级");

        List<AgentChatRequest> requests = new ArrayList<>();
        when(structuredRetryPolicy.execute(any(), any())).thenAnswer(invocation -> {
            Function<String, AgentChatRequest> factory = invocation.getArgument(0);
            requests.add(factory.apply(null));
            requests.add(factory.apply("纠正要求：必须包含 vocabulary 数组"));
            return new AgentChatResponse();
        });

        service().generateSceneWithWords(plan, 2,
                List.of(new LearningPlanSceneContentService.SceneCandidate("abandon", "/əˈbændən/", "放弃")),
                List.of(), 1, null);

        assertThat(requests).hasSize(2);
        assertThat(requests.get(0).getMessage())
                .contains("四级词汇冲刺")
                .doesNotContain("纠正要求");
        assertThat(requests.get(1).getMessage())
                .contains("四级词汇冲刺")
                .contains("纠正要求：必须包含 vocabulary 数组");
        // 两次请求的业务输入完全一致，只有用户提示末尾多了纠正要求
        assertThat(requests.get(1).getVariables()).isEqualTo(requests.get(0).getVariables());
        assertThat(requests.get(1).getModelConfigId()).isEqualTo(requests.get(0).getModelConfigId());
    }

    @Test
    void retryCorrectionRepeatsRequiredSceneFields() {
        List<String> corrections = new ArrayList<>();
        when(structuredRetryPolicy.execute(any(), any())).thenAnswer(invocation -> {
            UnaryOperator<String> correctionBuilder = invocation.getArgument(1);
            corrections.add(correctionBuilder.apply("AI 返回内容缺少必要字段：vocabulary"));
            return new AgentChatResponse();
        });

        LearningPlan plan = new LearningPlan();
        plan.setId(1L);
        plan.setUserId(2L);
        plan.setName("四级词汇冲刺");
        service().generateSceneWithWords(plan, 1, List.of(), List.of(), 0, null);

        assertThat(corrections).singleElement().satisfies(correction -> assertThat(correction)
                .contains("title")
                .contains("learning_text")
                .contains("translation")
                .contains("vocabulary"));
    }
}
