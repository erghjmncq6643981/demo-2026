package com.chandler.learning.agent.reading.api.controller;

import com.chandler.learning.agent.reading.api.request.ArticleStudyCompleteRequest;
import com.chandler.learning.agent.reading.api.request.ArticleStudyProgressRequest;
import com.chandler.learning.agent.reading.api.request.ArticleStudyRequest;
import com.chandler.learning.agent.reading.api.response.ArticleStudyResponse;
import com.chandler.learning.agent.reading.api.response.ArticleStudyPageResponse;
import com.chandler.learning.agent.identity.domain.entity.LearningUser;
import com.chandler.learning.agent.reading.application.ArticleStudyService;
import com.chandler.learning.agent.security.CurrentUserContext;
import com.chandler.learning.agent.task.domain.constant.AiTaskConstants;
import com.chandler.learning.agent.task.api.response.AiAsyncTaskResponse;
import com.chandler.learning.agent.task.application.AiAsyncTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

/**
 * ArticleStudyController 类。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/learning/articles")
@Tag(name = "语境精读")
public class ArticleStudyController {

    private final CurrentUserContext currentUserContext;
    private final ArticleStudyService articleStudyService;
    private final AiAsyncTaskService aiAsyncTaskService;
    private final ObjectMapper objectMapper;

    /**
     * 处理 {@code study} 相关业务。
     */
    @PostMapping("/study")
    @Operation(summary = "基于单词本目标词生成语境精读材料")
    public ArticleStudyResponse study(
            @Valid @RequestBody ArticleStudyRequest request) {
        LearningUser user = currentUserContext.requireUser();
        return articleStudyService.study(user.getId(), request);
    }

    @PostMapping("/study/async")
    @Operation(summary = "提交异步语境精读材料生成任务")
    public AiAsyncTaskResponse studyAsync(
            @Valid @RequestBody ArticleStudyRequest request) {
        LearningUser user = currentUserContext.requireUser();
        Map<String, Object> payload = objectMapper.convertValue(request, new TypeReference<>() {
        });
        String idempotencyKey = "article_material:" + user.getId() + ":" + request.getWordbookId()
                + ":" + String.valueOf(request.getEntryIds()) + ":" + request.getWordCountRange()
                + ":" + request.getDifficulty();
        var active = aiAsyncTaskService.findActiveByKey(user.getId(),
                AiTaskConstants.TYPE_ARTICLE_MATERIAL, null, idempotencyKey);
        if (active != null) return aiAsyncTaskService.toResponse(active);
        var task = aiAsyncTaskService.create(user.getId(), AiTaskConstants.TYPE_ARTICLE_MATERIAL,
                "生成语境精读材料", null, null, null,
                AiTaskConstants.EXECUTION_IMMEDIATE, null, null, 1, idempotencyKey, payload);
        return aiAsyncTaskService.toResponse(task);
    }

    /**
     * 查询 {@code listRecords} 相关业务。
     */
    @GetMapping
    @Operation(summary = "语境精读历史记录")
    public ArticleStudyPageResponse listRecords(
            @RequestParam(required = false) Long wordbookId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        LearningUser user = currentUserContext.requireUser();
        return articleStudyService.listRecords(user.getId(), wordbookId, page, pageSize);
    }

    /**
     * 查询 {@code detail} 相关业务。
     */
    @GetMapping("/{recordId}")
    @Operation(summary = "语境精读记录详情")
    public ArticleStudyResponse detail(
            @PathVariable Long recordId) {
        LearningUser user = currentUserContext.requireUser();
        return articleStudyService.detail(user.getId(), recordId);
    }

    @PostMapping("/{recordId}/progress")
    @Operation(summary = "开始语境精读或切换学习阶段")
    public ArticleStudyResponse updateProgress(
            @PathVariable Long recordId,
            @Valid @RequestBody ArticleStudyProgressRequest request) {
        LearningUser user = currentUserContext.requireUser();
        return articleStudyService.updateProgress(user.getId(), recordId, request);
    }

    @PostMapping("/{recordId}/complete")
    @Operation(summary = "提交阅读检测并完成语境精读")
    public ArticleStudyResponse complete(
            @PathVariable Long recordId,
            @Valid @RequestBody ArticleStudyCompleteRequest request) {
        LearningUser user = currentUserContext.requireUser();
        return articleStudyService.complete(user.getId(), recordId, request);
    }
}
