package com.chandler.learning.agent.reading.application.task;

import com.chandler.learning.agent.reading.api.request.ArticleStudyRequest;
import com.chandler.learning.agent.reading.api.response.ArticleStudyResponse;
import com.chandler.learning.agent.reading.application.ArticleStudyService;
import com.chandler.learning.agent.task.domain.constant.AiTaskConstants;
import com.chandler.learning.agent.task.application.AiAsyncTaskService;
import com.chandler.learning.agent.task.application.AiTaskExecutionService;
import com.chandler.learning.agent.task.application.contract.AiTaskHandler;
import com.chandler.learning.agent.task.application.contract.AiTaskPayload;
import com.chandler.learning.agent.task.application.contract.AiTaskStepDefinition;
import com.chandler.learning.agent.task.domain.entity.AiAsyncTask;
import com.chandler.learning.agent.task.domain.enums.AiTaskType;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** 语境精读材料的可恢复任务处理器；文章生成完成后才写入正式阅读记录。 */
@Component
@RequiredArgsConstructor
public class ArticleMaterialTaskHandler implements AiTaskHandler {

    private final ArticleStudyService articleStudyService;
    private final AiTaskExecutionService executionService;
    private final AiAsyncTaskService taskService;
    private final ObjectMapper objectMapper;

    /** 返回处理器支持的任务类型。 */
    @Override
    public AiTaskType taskType() {
        return AiTaskType.ARTICLE_MATERIAL;
    }

    /** 定义任务的执行步骤。 */
    @Override
    public List<AiTaskStepDefinition> steps() {
        return List.of(new AiTaskStepDefinition("generate_article", "生成并保存语境精读材料", 10));
    }

    /** 执行当前任务处理流程。 */
    @Override
    public void execute(AiAsyncTask task, Map<String, Object> payload) {
        executionService.execute(task.getId(), "generate_article", task.getOperatorUserId(),
                AiTaskPayload.longValue(payload, "modelConfigId"), () -> {
                    ArticleStudyRequest request = objectMapper.convertValue(payload, ArticleStudyRequest.class);
                    ArticleStudyResponse response = articleStudyService.study(task.getOwnerUserId(), request);
                    if (response.getId() != null) {
                        taskService.bindBusiness(task.getId(), "article_study", String.valueOf(response.getId()));
                    }
                    return request.getWordbookId();
                });
        taskService.updateProgress(task.getId(), 1, 1, 0);
        taskService.complete(task.getId(), AiTaskConstants.STATUS_COMPLETED, null);
    }
}
