package com.chandler.learning.agent.vocabulary.application.task;

import com.chandler.learning.agent.task.application.contract.AiTaskHandler;
import com.chandler.learning.agent.task.application.contract.AiTaskPayload;
import com.chandler.learning.agent.task.application.contract.AiTaskStepDefinition;
import com.chandler.learning.agent.task.domain.entity.AiAsyncTask;
import com.chandler.learning.agent.task.domain.enums.AiTaskType;
import com.chandler.learning.agent.vocabulary.application.VocabularyCardBatchService;
import com.chandler.learning.agent.task.application.AiTaskExecutionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** 词汇域批量词卡任务处理器，明细断点仍由词卡 Job/Item 表负责。 */
@Component
@RequiredArgsConstructor
public class VocabularyCardTaskHandler implements AiTaskHandler {

    private final VocabularyCardBatchService batchService;
    private final AiTaskExecutionService executionService;

    @Override
    public AiTaskType taskType() {
        return AiTaskType.VOCABULARY_CARD;
    }

    @Override
    public List<AiTaskStepDefinition> steps() {
        return List.of(new AiTaskStepDefinition("generate_cards", "分批生成缺失词卡", 10));
    }

    @Override
    public void execute(AiAsyncTask task, Map<String, Object> payload) {
        Long modelConfigId = AiTaskPayload.longValue(payload, "modelConfigId");
        executionService.execute(task.getId(), "generate_cards", task.getOperatorUserId(), modelConfigId, () -> {
            batchService.executeJob(task.getOwnerUserId(), task.getRelatedJobId(), modelConfigId);
            return task.getRelatedJobId();
        });
    }
}
