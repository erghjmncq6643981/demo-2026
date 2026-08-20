package com.chandler.learning.agent.task.application;

import com.chandler.learning.agent.task.domain.AiAsyncTask;
import com.chandler.learning.agent.exception.AiAsyncTaskCancelledException;
import com.chandler.learning.agent.learning.application.LearningPlanService;
import com.chandler.learning.agent.vocabulary.application.VocabularyCardBatchService;
import com.chandler.learning.agent.vocabulary.application.VocabularyCatalogAnalysisService;
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
            if (taskService.isCancelled(task.getId())) {
                return;
            }
            Map<String, Object> payload = readPayload(task.getPayloadJson());
            if (LearningConstants.AiTask.TYPE_SCENE_MATERIAL.equals(task.getTaskType())) {
                Long modelConfigId = number(payload.get("modelConfigId"));
                LocalDate recommendedDate = date(payload.get("recommendedDate"));
                int count = learningPlanService.generateNextUnit(
                        task.getUserId(), task.getPlanId(), modelConfigId, recommendedDate, task.getId()).size();
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
        } catch (AiAsyncTaskCancelledException ex) {
            log.info("用户取消 AI 异步任务，Worker 已停止 taskId={} type={} userId={}",
                    task.getId(), task.getTaskType(), task.getUserId());
        } catch (RuntimeException ex) {
            taskService.complete(task.getId(), LearningConstants.AiTask.STATUS_FAILED, ex.getMessage());
            log.info("AI 异步任务执行失败 taskId={} type={} userId={}",
                    task.getId(), task.getTaskType(), task.getUserId());
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
