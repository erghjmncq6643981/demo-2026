package com.chandler.learning.agent.reading.api.controller;

import com.chandler.learning.agent.reading.api.request.ArticleStudyCompleteRequest;
import com.chandler.learning.agent.reading.api.request.ArticleStudyProgressRequest;
import com.chandler.learning.agent.reading.api.request.ArticleStudyRequest;
import com.chandler.learning.agent.reading.api.response.ArticleStudyResponse;
import com.chandler.learning.agent.reading.api.response.ArticleStudyPageResponse;
import com.chandler.learning.agent.identity.domain.entity.LearningUser;
import com.chandler.learning.agent.reading.application.ArticleStudyService;
import com.chandler.learning.agent.reading.application.ArticleStudyTaskSubmissionService;
import com.chandler.learning.agent.security.CurrentUserContext;
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

/**
 * 语境精读接口控制器。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/learning/articles")
@Tag(name = "语境精读")
public class ArticleStudyController {

    private final CurrentUserContext currentUserContext;
    private final ArticleStudyService articleStudyService;
    private final ArticleStudyTaskSubmissionService taskSubmissionService;
    private final AiAsyncTaskService aiAsyncTaskService;

    /** 兼容旧路径：提交语境精读材料任务，不在 HTTP 请求中等待 AI。 */
    @PostMapping("/study")
    @Operation(summary = "提交语境精读材料任务（兼容路径）")
    public AiAsyncTaskResponse study(
            @Valid @RequestBody ArticleStudyRequest request) {
        LearningUser user = currentUserContext.requireUser();
        return aiAsyncTaskService.toResponse(taskSubmissionService.submit(user.getId(), request));
    }

    /** 提交异步语境精读材料生成任务。 */
    @PostMapping("/study/async")
    @Operation(summary = "提交异步语境精读材料生成任务")
    public AiAsyncTaskResponse studyAsync(
        @Valid @RequestBody ArticleStudyRequest request) {
        LearningUser user = currentUserContext.requireUser();
        return aiAsyncTaskService.toResponse(taskSubmissionService.submit(user.getId(), request));
    }

    /** 语境精读历史记录。 */
    @GetMapping
    @Operation(summary = "语境精读历史记录")
    public ArticleStudyPageResponse listRecords(
            @RequestParam(required = false) Long wordbookId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        LearningUser user = currentUserContext.requireUser();
        return articleStudyService.listRecords(user.getId(), wordbookId, page, pageSize);
    }

    /** 语境精读记录详情。 */
    @GetMapping("/{recordId}")
    @Operation(summary = "语境精读记录详情")
    public ArticleStudyResponse detail(
            @PathVariable Long recordId) {
        LearningUser user = currentUserContext.requireUser();
        return articleStudyService.detail(user.getId(), recordId);
    }

    /** 开始语境精读或切换学习阶段。 */
    @PostMapping("/{recordId}/progress")
    @Operation(summary = "开始语境精读或切换学习阶段")
    public ArticleStudyResponse updateProgress(
            @PathVariable Long recordId,
            @Valid @RequestBody ArticleStudyProgressRequest request) {
        LearningUser user = currentUserContext.requireUser();
        return articleStudyService.updateProgress(user.getId(), recordId, request);
    }

    /** 提交阅读检测并完成语境精读。 */
    @PostMapping("/{recordId}/complete")
    @Operation(summary = "提交阅读检测并完成语境精读")
    public ArticleStudyResponse complete(
            @PathVariable Long recordId,
            @Valid @RequestBody ArticleStudyCompleteRequest request) {
        LearningUser user = currentUserContext.requireUser();
        return articleStudyService.complete(user.getId(), recordId, request);
    }
}
