package com.chandler.learning.agent.vocabulary.application.task;

import com.chandler.learning.agent.task.application.contract.AiTaskHandler;
import com.chandler.learning.agent.task.application.contract.AiTaskPayload;
import com.chandler.learning.agent.task.application.contract.AiTaskStepDefinition;
import com.chandler.learning.agent.task.domain.AiAsyncTask;
import com.chandler.learning.agent.task.domain.AiTaskType;
import com.chandler.learning.agent.vocabulary.application.VocabularyCatalogAnalysisService;
import com.chandler.learning.agent.task.application.AiTaskExecutionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** 公共词本关联分析处理器，成功条目继续由分析 Batch/Item 持久化。 */
@Component
@RequiredArgsConstructor
public class VocabularyCatalogAnalysisTaskHandler implements AiTaskHandler {

    private final VocabularyCatalogAnalysisService analysisService;
    private final AiTaskExecutionService executionService;

    @Override
    public AiTaskType taskType() {
        return AiTaskType.VOCABULARY_CATALOG_ANALYSIS;
    }

    @Override
    public List<AiTaskStepDefinition> steps() {
        return List.of(new AiTaskStepDefinition("analyze_catalog", "分批分析公共词本", 10));
    }

    @Override
    public void execute(AiAsyncTask task, Map<String, Object> payload) {
        Long modelConfigId = AiTaskPayload.longValue(payload, "modelConfigId");
        executionService.execute(task.getId(), "analyze_catalog", task.getOperatorUserId(), modelConfigId, () -> {
            analysisService.executeJob(task.getOwnerUserId(), task.getRelatedJobId(), modelConfigId);
            return task.getRelatedJobId();
        });
    }
}
