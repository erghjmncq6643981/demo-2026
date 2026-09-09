package com.chandler.learning.agent.vocabulary.application.task;

import com.chandler.learning.agent.task.application.AiAsyncTaskService;
import com.chandler.learning.agent.task.application.AiTaskExecutionService;
import com.chandler.learning.agent.task.application.contract.AiTaskHandler;
import com.chandler.learning.agent.task.application.contract.AiTaskPayload;
import com.chandler.learning.agent.task.application.contract.AiTaskStepDefinition;
import com.chandler.learning.agent.task.domain.constant.AiTaskConstants;
import com.chandler.learning.agent.task.domain.entity.AiAsyncTask;
import com.chandler.learning.agent.task.domain.enums.AiTaskType;
import com.chandler.learning.agent.vocabulary.application.WordbookService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** 单词本单词卡异步生成处理器，避免用户请求线程直接等待模型响应。 */
@Component
@RequiredArgsConstructor
public class VocabularySingleCardTaskHandler implements AiTaskHandler {

    private final WordbookService wordbookService;
    private final AiTaskExecutionService executionService;
    private final AiAsyncTaskService taskService;

    /** 返回单词词卡异步任务类型。 */
    @Override
    public AiTaskType taskType() {
        return AiTaskType.VOCABULARY_CARD_SINGLE;
    }

    /** 定义单词词卡生成步骤及展示名称。 */
    @Override
    public List<AiTaskStepDefinition> steps() {
        return List.of(new AiTaskStepDefinition("generate_card", "生成单词词卡", 10));
    }

    /** 执行单词词卡生成并更新任务进度。 */
    @Override
    public void execute(AiAsyncTask task, Map<String, Object> payload) {
        Long entryId = task.getRelatedJobId() != null
                ? task.getRelatedJobId() : AiTaskPayload.longValue(payload, "entryId");
        boolean forceRefresh = payload != null && Boolean.parseBoolean(String.valueOf(payload.get("forceRefresh")));
        Long modelConfigId = AiTaskPayload.longValue(payload, "modelConfigId");
        try {
            executionService.execute(task.getId(), "generate_card", task.getOperatorUserId(), modelConfigId,
                    () -> wordbookService.generateCardNow(task.getOwnerUserId(), entryId, forceRefresh));
        } catch (RuntimeException ex) {
            wordbookService.markCardGenerationFailed(task.getOwnerUserId(), entryId, ex.getMessage());
            throw ex;
        }
        taskService.updateProgress(task.getId(), 1, 1, 0);
        taskService.complete(task.getId(), AiTaskConstants.STATUS_COMPLETED, null);
    }
}
