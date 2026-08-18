package com.chandler.learning.agent.service.learning;

import com.chandler.learning.agent.domain.entity.learning.AiAsyncTask;
import com.chandler.learning.agent.service.vocabulary.VocabularyCardBatchService;
import com.chandler.learning.agent.service.vocabulary.VocabularyCatalogAnalysisService;
import com.chandler.learning.agent.support.LearningConstants;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Map;

/**
 * 将已领取的任务路由到具体业务 Worker。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiAsyncTaskDispatcher {

    private final AiAsyncTaskService taskService;
    private final LearningPlanService learningPlanService;
    private final VocabularyCardBatchService vocabularyCardBatchService;
    private final VocabularyCatalogAnalysisService vocabularyCatalogAnalysisService;
    private final ObjectMapper objectMapper;

    @Async("aiTaskExecutor")
    public void dispatch(AiAsyncTask task) {
        try {
            Map<String, Object> payload = readPayload(task.getPayloadJson());
            if (LearningConstants.AiTask.TYPE_SCENE_MATERIAL.equals(task.getTaskType())) {
                Long modelConfigId = number(payload.get("modelConfigId"));
                LocalDate recommendedDate = date(payload.get("recommendedDate"));
                int count = learningPlanService.generateNextUnit(
                        task.getUserId(), task.getPlanId(), modelConfigId, recommendedDate).size();
                taskService.updateProgress(task.getId(), 1, count > 0 ? 1 : 0, count > 0 ? 0 : 1);
                taskService.complete(task.getId(), count > 0
                        ? LearningConstants.AiTask.STATUS_COMPLETED
                        : LearningConstants.AiTask.STATUS_FAILED, null);
                return;
            }
            if (LearningConstants.AiTask.TYPE_VOCABULARY_CARD.equals(task.getTaskType())) {
                vocabularyCardBatchService.executeJob(task.getUserId(), task.getRelatedJobId(),
                        number(payload.get("modelConfigId")));
                return;
            }
            if (LearningConstants.AiTask.TYPE_VOCABULARY_CATALOG_ANALYSIS.equals(task.getTaskType())) {
                vocabularyCatalogAnalysisService.executeJob(task.getUserId(), task.getRelatedJobId(),
                        number(payload.get("modelConfigId")));
                return;
            }
            taskService.complete(task.getId(), LearningConstants.AiTask.STATUS_FAILED, "不支持的 AI 任务类型");
        } catch (RuntimeException ex) {
            taskService.complete(task.getId(), LearningConstants.AiTask.STATUS_FAILED, ex.getMessage());
            log.error("AI 异步任务执行失败 taskId={} type={} error={}", task.getId(), task.getTaskType(), ex.getMessage());
            log.debug("AI 异步任务异常详情 taskId={}", task.getId(), ex);
        }
    }

    private Map<String, Object> readPayload(String payloadJson) {
        try {
            return objectMapper.readValue(payloadJson == null ? "{}" : payloadJson,
                    new TypeReference<>() {
                    });
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private Long number(Object value) {
        if (value == null) return null;
        try {
            return value instanceof Number number ? number.longValue() : Long.valueOf(value.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private LocalDate date(Object value) {
        if (value == null) return null;
        try {
            return LocalDate.parse(value.toString());
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
