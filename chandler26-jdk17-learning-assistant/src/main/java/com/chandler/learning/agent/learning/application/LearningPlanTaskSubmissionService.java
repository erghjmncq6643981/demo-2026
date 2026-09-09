package com.chandler.learning.agent.learning.application;

import com.chandler.learning.agent.common.exception.LearningErrorCode;
import com.chandler.learning.agent.exception.LearningAssistantException;
import com.chandler.learning.agent.learning.domain.constant.ScenePlanConstants;
import com.chandler.learning.agent.task.application.AiAsyncTaskService;
import com.chandler.learning.agent.task.domain.constant.AiTaskConstants;
import com.chandler.learning.agent.task.domain.entity.AiAsyncTask;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 学习计划 AI 任务提交边界。
 * <p>
 * 只负责校验轻量业务状态、组装幂等任务和持久化任务，不调用模型；实际生成由任务 Worker 在提交后执行。
 */
@Service
@RequiredArgsConstructor
public class LearningPlanTaskSubmissionService {

    private final LearningPlanAccessService planAccessService;
    private final AiAsyncTaskService taskService;

    /** 提交指定日期的下一批场景材料任务。 */
    public AiAsyncTask submitNext(Long userId, Long planId, Long modelConfigId,
                                  LocalDate recommendedDate, String executionMode,
                                  LocalDateTime scheduledTime, Integer priority) {
        var plan = planAccessService.requireOwnedPlan(userId, planId);
        ensureActive(plan.getStatus());
        LocalDate taskDate = recommendedDate == null ? LocalDate.now() : recommendedDate;
        String idempotencyKey = AiTaskConstants.TYPE_SCENE_MATERIAL + ":" + planId + ":" + taskDate;
        AiAsyncTask active = taskService.findActiveSceneMaterialTask(userId, planId, idempotencyKey);
        if (active != null) {
            return active;
        }
        Map<String, Object> payload = new HashMap<>();
        if (modelConfigId != null) {
            payload.put("modelConfigId", modelConfigId);
        }
        payload.put("recommendedDate", taskDate.toString());
        return taskService.create(userId,
                AiTaskConstants.TYPE_SCENE_MATERIAL,
                "生成 " + taskDate + " 场景材料",
                planId, null, null,
                executionMode, scheduledTime, priority, 1,
                idempotencyKey, payload);
    }

    /** 提交指定日期的场景材料版本重生成任务。 */
    public AiAsyncTask submitRegeneration(Long userId, Long planId, Long modelConfigId,
                                          LocalDate recommendedDate) {
        var plan = planAccessService.requireOwnedPlan(userId, planId);
        ensureActive(plan.getStatus());
        if (recommendedDate == null) {
            throw LearningAssistantException.badRequest(
                    LearningErrorCode.LEARNING_PLAN_STATE_ERROR,
                    "请指定要重新生成的日期");
        }
        String idempotencyKey = AiTaskConstants.TYPE_SCENE_MATERIAL_REGENERATION + ":" + planId + ":" + recommendedDate;
        AiAsyncTask active = taskService.findActiveByKey(userId,
                AiTaskConstants.TYPE_SCENE_MATERIAL_REGENERATION, planId, idempotencyKey);
        if (active != null) {
            return active;
        }
        Map<String, Object> payload = new HashMap<>();
        if (modelConfigId != null) {
            payload.put("modelConfigId", modelConfigId);
        }
        payload.put("recommendedDate", recommendedDate.toString());
        return taskService.create(userId,
                AiTaskConstants.TYPE_SCENE_MATERIAL_REGENERATION,
                "重新生成 " + recommendedDate + " 场景材料",
                planId, null, null,
                AiTaskConstants.EXECUTION_IMMEDIATE, null, null, 1,
                idempotencyKey, payload);
    }

    /** 创建计划后的首个场景只提交一个父任务，后续步骤由 Worker 继续完成。 */
    public AiAsyncTask submitInitial(Long userId, Long planId, Long modelConfigId) {
        return submitNext(userId, planId, modelConfigId, LocalDate.now(),
                AiTaskConstants.EXECUTION_IMMEDIATE, null, 3);
    }

    private void ensureActive(String status) {
        if (!StringUtils.hasText(status) || !ScenePlanConstants.STATUS_ACTIVE.equals(status)) {
            throw LearningAssistantException.badRequest(
                    LearningErrorCode.LEARNING_PLAN_STATE_ERROR,
                    "只有进行中的学习计划可以生成场景材料");
        }
    }
}
