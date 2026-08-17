package com.chandler.learning.agent.controller.learning;

import com.chandler.learning.agent.domain.dto.learning.LearningAssessmentSubmitRequest;
import com.chandler.learning.agent.domain.dto.learning.LearningAssessmentSubmitResponse;
import com.chandler.learning.agent.domain.dto.learning.LearningPlanCreateRequest;
import com.chandler.learning.agent.domain.dto.learning.LearningPlanUpdateRequest;
import com.chandler.learning.agent.domain.dto.learning.LearningPlanNextUnitRequest;
import com.chandler.learning.agent.domain.dto.learning.LearningPlanResponse;
import com.chandler.learning.agent.domain.dto.learning.LearningPlanUnitEntryResponse;
import com.chandler.learning.agent.domain.dto.learning.LearningPlanUnitResponse;
import com.chandler.learning.agent.domain.entity.learning.LearningUser;
import com.chandler.learning.agent.domain.dto.vocabulary.VocabularyCardGenerationRequest;
import com.chandler.learning.agent.domain.dto.vocabulary.VocabularyCardGenerationResponse;
import com.chandler.learning.agent.service.learning.AuthService;
import com.chandler.learning.agent.service.learning.LearningPlanService;
import com.chandler.learning.agent.service.vocabulary.VocabularyCardBatchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

import java.util.List;

/**
 * 场景化词汇学习计划接口。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/learning/plans")
@Tag(name = "场景词汇学习计划")
public class LearningPlanController {

    private final AuthService authService;
    private final LearningPlanService learningPlanService;
    private final VocabularyCardBatchService cardBatchService;

    @PostMapping
    @Operation(summary = "根据已发布词表创建场景学习计划")
    public LearningPlanResponse create(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody LearningPlanCreateRequest request) {
        LearningUser user = authService.requireUser(authorization);
        return learningPlanService.create(user.getId(), request);
    }

    @PutMapping("/{planId}")
    @Operation(summary = "更新场景学习计划")
    public LearningPlanResponse update(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long planId,
            @Valid @RequestBody LearningPlanUpdateRequest request) {
        LearningUser user = authService.requireUser(authorization);
        return learningPlanService.update(user.getId(), planId, request);
    }

    @GetMapping
    @Operation(summary = "我的场景学习计划")
    public List<LearningPlanResponse> list(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        LearningUser user = authService.requireUser(authorization);
        return learningPlanService.list(user.getId());
    }

    @GetMapping("/{planId}")
    @Operation(summary = "场景学习计划详情")
    public LearningPlanResponse detail(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long planId) {
        LearningUser user = authService.requireUser(authorization);
        return learningPlanService.detail(user.getId(), planId);
    }
    @PostMapping("/{planId}/pause")
    @Operation(summary = "暂停场景学习计划")
    public LearningPlanResponse pause(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long planId) {
        LearningUser user = authService.requireUser(authorization);
        return learningPlanService.pause(user.getId(), planId);
    }

    @PostMapping("/{planId}/resume")
    @Operation(summary = "恢复/启动场景学习计划")
    public LearningPlanResponse resume(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long planId) {
        LearningUser user = authService.requireUser(authorization);
        return learningPlanService.resume(user.getId(), planId);
    }

    @PostMapping("/{planId}/cancel")
    @Operation(summary = "取消场景学习计划")
    public LearningPlanResponse cancel(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long planId) {
        LearningUser user = authService.requireUser(authorization);
        return learningPlanService.cancel(user.getId(), planId);
    }

    @PostMapping("/{planId}/units/next")
    @Operation(summary = "手动生成下一个场景单元，不限制每日触发次数")
    public LearningPlanUnitResponse nextUnit(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long planId,
            @RequestBody(required = false) LearningPlanNextUnitRequest request) {
        LearningUser user = authService.requireUser(authorization);
        Long modelConfigId = request == null ? null : request.getModelConfigId();
        LocalDate recommendedDate = request == null ? null : request.getRecommendedDate();
        return learningPlanService.generateNextUnit(user.getId(), planId, modelConfigId, recommendedDate);
    }

    @PostMapping("/{planId}/units/{unitId}/start")
    @Operation(summary = "开始或切换到已生成的场景单元")
    public LearningPlanResponse startUnit(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long planId,
            @PathVariable Long unitId) {
        LearningUser user = authService.requireUser(authorization);
        return learningPlanService.startUnit(user.getId(), planId, unitId);
    }

    @PostMapping("/{planId}/units/{unitId}/assessments")
    @Operation(summary = "提交含义选择、跟敲或按含义拼写检查")
    public LearningAssessmentSubmitResponse submitAssessment(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long planId,
            @PathVariable Long unitId,
            @Valid @RequestBody LearningAssessmentSubmitRequest request) {
        LearningUser user = authService.requireUser(authorization);
        return learningPlanService.submitAssessment(user.getId(), planId, unitId, request);
    }

    @PostMapping("/{planId}/units/{unitId}/complete")
    @Operation(summary = "完成当前场景，后续单元仍由学习者手动触发")
    public LearningPlanResponse completeUnit(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long planId,
            @PathVariable Long unitId) {
        LearningUser user = authService.requireUser(authorization);
        return learningPlanService.completeUnit(user.getId(), planId, unitId);
    }

    @PostMapping("/{planId}/units/{unitId}/entries/{entryId}/promote")
    @Operation(summary = "把扩展或 AI 补充词提升为核心学习词")
    public LearningPlanUnitEntryResponse promoteWord(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long planId,
            @PathVariable Long unitId,
            @PathVariable Long entryId) {
        LearningUser user = authService.requireUser(authorization);
        return learningPlanService.promoteWord(user.getId(), planId, unitId, entryId);
    }

    @PostMapping("/{planId}/units/{unitId}/cards/generate")
    @Operation(summary = "对当前场景缓存缺失词按 10-20 个一批生成 AI 词卡")
    public VocabularyCardGenerationResponse generateCards(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long planId,
            @PathVariable Long unitId,
            @RequestBody(required = false) VocabularyCardGenerationRequest request) {
        LearningUser user = authService.requireUser(authorization);
        return cardBatchService.generate(user.getId(), planId, unitId, request);
    }
}
