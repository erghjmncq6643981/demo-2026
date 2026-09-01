package com.chandler.learning.agent.learning.api.controller;

import com.chandler.learning.agent.learning.api.request.LearningAssessmentSubmitRequest;
import com.chandler.learning.agent.learning.api.response.LearningAssessmentSubmitResponse;
import com.chandler.learning.agent.learning.api.response.LearningPlanCalendarDayResponse;
import com.chandler.learning.agent.learning.api.request.LearningPlanCreateRequest;
import com.chandler.learning.agent.learning.api.request.LearningPlanNextUnitRequest;
import com.chandler.learning.agent.learning.api.response.LearningPlanResponse;
import com.chandler.learning.agent.learning.api.request.LearningPlanUpdateRequest;
import com.chandler.learning.agent.learning.api.response.LearningPlanUnitEntryResponse;
import com.chandler.learning.agent.learning.api.response.LearningPlanUnitResponse;
import com.chandler.learning.agent.task.api.response.AiAsyncTaskResponse;
import com.chandler.learning.agent.task.api.request.AiAsyncTaskScheduleRequest;
import com.chandler.learning.agent.identity.domain.entity.LearningUser;
import com.chandler.learning.agent.vocabulary.api.request.VocabularyCardGenerationRequest;
import com.chandler.learning.agent.vocabulary.api.response.VocabularyCardGenerationResponse;
import com.chandler.learning.agent.learning.domain.entity.LearningPlanUnit;
import com.chandler.learning.agent.security.CurrentUserContext;
import com.chandler.learning.agent.learning.application.LearningPlanService;
import com.chandler.learning.agent.task.application.AiAsyncTaskService;
import com.chandler.learning.agent.vocabulary.application.VocabularyCardBatchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.chandler.learning.agent.config.web.annotation.ApiAccessLog;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

/**
 * 场景化词汇学习计划接口。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/learning/plans")
@Tag(name = "场景词汇学习计划")
@ApiAccessLog("场景词汇学习计划")
public class LearningPlanController {

    private final CurrentUserContext currentUserContext;
    private final LearningPlanService learningPlanService;
    private final VocabularyCardBatchService cardBatchService;
    private final AiAsyncTaskService aiAsyncTaskService;

    /** 根据已发布词表创建场景学习计划。 */
    @PostMapping
    @Operation(summary = "根据已发布词表创建场景学习计划")
    public LearningPlanResponse create(
            @Valid @RequestBody LearningPlanCreateRequest request) {
        LearningUser user = currentUserContext.requireUser();
        return learningPlanService.create(user.getId(), request);
    }

    /** 更新场景学习计划。 */
    @PutMapping("/{planId}")
    @Operation(summary = "更新场景学习计划")
    public LearningPlanResponse update(
            @PathVariable Long planId,
            @Valid @RequestBody LearningPlanUpdateRequest request) {
        LearningUser user = currentUserContext.requireUser();
        return learningPlanService.update(user.getId(), planId, request);
    }

    /** 我的场景学习计划。 */
    @GetMapping
    @Operation(summary = "我的场景学习计划")
    public List<LearningPlanResponse> list() {
        LearningUser user = currentUserContext.requireUser();
        return learningPlanService.list(user.getId());
    }

    /** 场景学习计划详情。 */
    @GetMapping("/{planId}")
    @Operation(summary = "场景学习计划详情")
    public LearningPlanResponse detail(
            @PathVariable Long planId) {
        LearningUser user = currentUserContext.requireUser();
        return learningPlanService.detail(user.getId(), planId);
    }

    /** 查询学习计划日历汇总。 */
    @GetMapping("/{planId}/calendar")
    @Operation(summary = "查询学习计划日历汇总")
    public List<LearningPlanCalendarDayResponse> calendar(
            @PathVariable Long planId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LearningUser user = currentUserContext.requireUser();
        return learningPlanService.calendar(user.getId(), planId, from, to);
    }

    /** 按需加载单个场景的完整学习内容。 */
    @GetMapping("/{planId}/units/{unitId}")
    @Operation(summary = "按需加载单个场景的完整学习内容")
    public LearningPlanUnitResponse unitDetail(
            @PathVariable Long planId,
            @PathVariable Long unitId) {
        LearningUser user = currentUserContext.requireUser();
        return learningPlanService.unitDetail(user.getId(), planId, unitId);
    }

    /** 暂停场景学习计划。 */
    @PostMapping("/{planId}/pause")
    @Operation(summary = "暂停场景学习计划")
    public LearningPlanResponse pause(
            @PathVariable Long planId) {
        LearningUser user = currentUserContext.requireUser();
        return learningPlanService.pause(user.getId(), planId);
    }

    /** 恢复/启动场景学习计划。 */
    @PostMapping("/{planId}/resume")
    @Operation(summary = "恢复/启动场景学习计划")
    public LearningPlanResponse resume(
            @PathVariable Long planId) {
        LearningUser user = currentUserContext.requireUser();
        return learningPlanService.resume(user.getId(), planId);
    }

    /** 取消场景学习计划。 */
    @PostMapping("/{planId}/cancel")
    @Operation(summary = "取消场景学习计划")
    public LearningPlanResponse cancel(
            @PathVariable Long planId) {
        LearningUser user = currentUserContext.requireUser();
        return learningPlanService.cancel(user.getId(), planId);
    }

    /** 按学习计划生成指定日期的场景材料，超过 50 词自动均分多篇。 */
    @PostMapping("/{planId}/units/next")
    @Operation(summary = "按学习计划生成指定日期的场景材料，超过 50 词自动均分多篇")
    public List<LearningPlanUnitResponse> nextUnit(
            @PathVariable Long planId,
            @RequestBody(required = false) LearningPlanNextUnitRequest request) {
        LearningUser user = currentUserContext.requireUser();
        Long modelConfigId = request == null ? null : request.getModelConfigId();
        LocalDate recommendedDate = request == null ? null : request.getRecommendedDate();
        return learningPlanService.generateNextUnit(user.getId(), planId, modelConfigId, recommendedDate);
    }

    /** 重新生成指定日期的场景材料。 */
    @PostMapping("/{planId}/units/regenerate-day")
    @Operation(summary = "重新生成指定日期的场景材料")
    public List<LearningPlanUnitResponse> regenerateDayUnits(
            @PathVariable Long planId,
            @Valid @RequestBody com.chandler.learning.agent.learning.api.request.LearningPlanRegenerateDayRequest request) {
        LearningUser user = currentUserContext.requireUser();
        return learningPlanService.regenerateDayUnits(user.getId(), planId, request.getModelConfigId(), request.getRecommendedDate());
    }

    /** 异步生成当天场景材料新版本，并保留旧版本历史。 */
    @PostMapping("/{planId}/units/regenerate-day/async")
    @Operation(summary = "异步生成当天场景材料新版本，并保留旧版本历史")
    public AiAsyncTaskResponse regenerateDayUnitsAsync(
            @PathVariable Long planId,
            @Valid @RequestBody com.chandler.learning.agent.learning.api.request.LearningPlanRegenerateDayRequest request) {
        LearningUser user = currentUserContext.requireUser();
        learningPlanService.detail(user.getId(), planId);
        String idempotencyKey = "scene_material_regeneration:" + planId + ":" + request.getRecommendedDate();
        var active = aiAsyncTaskService.findActiveByKey(user.getId(),
                com.chandler.learning.agent.task.domain.constant.AiTaskConstants.TYPE_SCENE_MATERIAL_REGENERATION,
                planId, idempotencyKey);
        if (active != null) return aiAsyncTaskService.toResponse(active);
        Map<String, Object> payload = new HashMap<>();
        if (request.getModelConfigId() != null) payload.put("modelConfigId", request.getModelConfigId());
        payload.put("recommendedDate", request.getRecommendedDate().toString());
        var task = aiAsyncTaskService.create(user.getId(),
                com.chandler.learning.agent.task.domain.constant.AiTaskConstants.TYPE_SCENE_MATERIAL_REGENERATION,
                 "重新生成 " + request.getRecommendedDate() + " 场景材料", planId, null, null,
                 com.chandler.learning.agent.task.domain.constant.AiTaskConstants.EXECUTION_IMMEDIATE,
                 null, null, 3, idempotencyKey, payload);
        return aiAsyncTaskService.toResponse(task);
    }

    /** 预约生成场景材料，任务由低价时段调度器执行。 */
    @PostMapping("/{planId}/units/next/async")
    @Operation(summary = "预约生成场景材料，任务由低价时段调度器执行")
    public AiAsyncTaskResponse scheduleNextUnit(
            @PathVariable Long planId,
            @RequestBody(required = false) AiAsyncTaskScheduleRequest request) {
        LearningUser user = currentUserContext.requireUser();
        AiAsyncTaskScheduleRequest resolved = request == null ? new AiAsyncTaskScheduleRequest() : request;
        learningPlanService.detail(user.getId(), planId);
        LocalDate taskDate = resolved.getRecommendedDate() == null ? LocalDate.now() : resolved.getRecommendedDate();
        String idempotencyKey = "scene_material:" + planId + ":" + taskDate;
        var activeTask = aiAsyncTaskService.findActiveSceneMaterialTask(user.getId(), planId, idempotencyKey);
        if (activeTask != null) {
            return aiAsyncTaskService.toResponse(activeTask);
        }
        Map<String, Object> payload = new HashMap<>();
        if (resolved.getModelConfigId() != null) payload.put("modelConfigId", resolved.getModelConfigId());
        if (resolved.getRecommendedDate() != null) payload.put("recommendedDate", resolved.getRecommendedDate().toString());
        var task = aiAsyncTaskService.create(user.getId(),
                com.chandler.learning.agent.task.domain.constant.AiTaskConstants.TYPE_SCENE_MATERIAL,
                 "批量生成 " + taskDate + " 场景材料", planId, null, null,
                 resolved.getExecutionMode(), resolved.getScheduledTime(), resolved.getPriority(), 1,
                 idempotencyKey, payload);
        return aiAsyncTaskService.toResponse(task);
    }

    /** 开始或切换到已生成的场景单元。 */
    @PostMapping("/{planId}/units/{unitId}/start")
    @Operation(summary = "开始或切换到已生成的场景单元")
    public LearningPlanResponse startUnit(
            @PathVariable Long planId,
            @PathVariable Long unitId) {
        LearningUser user = currentUserContext.requireUser();
        return learningPlanService.startUnit(user.getId(), planId, unitId);
    }

    /** 提交含义选择、跟敲或按含义拼写检查。 */
    @PostMapping("/{planId}/units/{unitId}/assessments")
    @Operation(summary = "提交含义选择、跟敲或按含义拼写检查")
    public LearningAssessmentSubmitResponse submitAssessment(
            @PathVariable Long planId,
            @PathVariable Long unitId,
            @Valid @RequestBody LearningAssessmentSubmitRequest request) {
        LearningUser user = currentUserContext.requireUser();
        return learningPlanService.submitAssessment(user.getId(), planId, unitId, request);
    }

    /** 完成当前场景，后续单元仍由学习者手动触发。 */
    @PostMapping("/{planId}/units/{unitId}/complete")
    @Operation(summary = "完成当前场景，后续单元仍由学习者手动触发")
    public LearningPlanResponse completeUnit(
            @PathVariable Long planId,
            @PathVariable Long unitId) {
        LearningUser user = currentUserContext.requireUser();
        return learningPlanService.completeUnit(user.getId(), planId, unitId);
    }

    /** 把扩展或 AI 补充词提升为核心学习词。 */
    @PostMapping("/{planId}/units/{unitId}/entries/{entryId}/promote")
    @Operation(summary = "把扩展或 AI 补充词提升为核心学习词")
    public LearningPlanUnitEntryResponse promoteWord(
            @PathVariable Long planId,
            @PathVariable Long unitId,
            @PathVariable Long entryId) {
        LearningUser user = currentUserContext.requireUser();
        return learningPlanService.promoteWord(user.getId(), planId, unitId, entryId);
    }

    /** 为已有场景材料独立补生成场景相关词汇。 */
    @PostMapping("/{planId}/units/{unitId}/related-words/async")
    @Operation(summary = "为已有场景材料独立补生成场景相关词汇")
    public AiAsyncTaskResponse generateRelatedWords(
            @PathVariable Long planId,
            @PathVariable Long unitId,
            @RequestBody(required = false) Map<String, Object> request) {
        LearningUser user = currentUserContext.requireUser();
        LearningPlanResponse plan = learningPlanService.detail(user.getId(), planId);
        List<LearningPlanUnit> units = learningPlanService.findUnitsByIds(planId, List.of(unitId));
        String unitTitle = (units != null && !units.isEmpty() && StringUtils.hasText(units.get(0).getTitle()))
                ? units.get(0).getTitle() : ("场景单元#" + unitId);
        String taskName = "《" + plan.getName() + "》" + unitTitle + " · 补充相关词汇";

        String idempotencyKey = "scene_related_vocabulary:" + planId + ":" + unitId;
        var active = aiAsyncTaskService.findActiveByKey(user.getId(),
                com.chandler.learning.agent.task.domain.constant.AiTaskConstants.TYPE_SCENE_RELATED_VOCABULARY,
                planId, idempotencyKey);
        if (active != null) return aiAsyncTaskService.toResponse(active);
        Map<String, Object> payload = request == null ? new HashMap<>() : new HashMap<>(request);
        payload.putIfAbsent("targetCount", 50);
        payload.put("planName", plan.getName());
        payload.put("unitTitle", unitTitle);
        payload.put("unitId", unitId);
        if (units != null && !units.isEmpty() && units.get(0).getRecommendedDate() != null) {
            payload.put("recommendedDate", units.get(0).getRecommendedDate().toString());
        }
        var task = aiAsyncTaskService.create(user.getId(),
                com.chandler.learning.agent.task.domain.constant.AiTaskConstants.TYPE_SCENE_RELATED_VOCABULARY,
                 taskName, planId, unitId, null,
                 com.chandler.learning.agent.task.domain.constant.AiTaskConstants.EXECUTION_IMMEDIATE,
                 null, null, 50, idempotencyKey, payload);
        return aiAsyncTaskService.toResponse(task);
    }

    /** 对当前场景缓存缺失词按 10-20 个一批生成 AI 词卡。 */
    @PostMapping("/{planId}/units/{unitId}/cards/generate")
    @Operation(summary = "对当前场景缓存缺失词按 10-20 个一批生成 AI 词卡")
    public VocabularyCardGenerationResponse generateCards(
            @PathVariable Long planId,
            @PathVariable Long unitId,
            @RequestBody(required = false) VocabularyCardGenerationRequest request) {
        LearningUser user = currentUserContext.requireUser();
        return cardBatchService.generate(user.getId(), planId, unitId, request);
    }
}
