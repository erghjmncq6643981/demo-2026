package com.chandler.learning.agent.reading.api;

import com.chandler.learning.agent.reading.api.ArticleStudyCompleteRequest;
import com.chandler.learning.agent.reading.api.ArticleStudyProgressRequest;
import com.chandler.learning.agent.reading.api.ArticleStudyRequest;
import com.chandler.learning.agent.reading.api.ArticleStudyResponse;
import com.chandler.learning.agent.identity.domain.LearningUser;
import com.chandler.learning.agent.reading.application.ArticleStudyService;
import com.chandler.learning.agent.identity.application.AuthService;
import com.chandler.learning.agent.support.LearningConstants;
import com.chandler.learning.agent.task.api.AiAsyncTaskResponse;
import com.chandler.learning.agent.task.application.AiAsyncTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
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

    private final AuthService authService;
    private final ArticleStudyService articleStudyService;
    private final AiAsyncTaskService aiAsyncTaskService;
    private final ObjectMapper objectMapper;

    /**
     * 处理 {@code study} 相关业务。
     */
    @PostMapping("/study")
    @Operation(summary = "基于单词本目标词生成语境精读材料")
    public ArticleStudyResponse study(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody ArticleStudyRequest request) {
        LearningUser user = authService.requireUser(authorization);
        return articleStudyService.study(user.getId(), request);
    }

    @PostMapping("/study/async")
    @Operation(summary = "提交异步语境精读材料生成任务")
    public AiAsyncTaskResponse studyAsync(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody ArticleStudyRequest request) {
        LearningUser user = authService.requireUser(authorization);
        Map<String, Object> payload = objectMapper.convertValue(request, new TypeReference<>() {
        });
        String idempotencyKey = "article_material:" + user.getId() + ":" + request.getWordbookId()
                + ":" + String.valueOf(request.getEntryIds()) + ":" + request.getWordCountRange()
                + ":" + request.getDifficulty();
        var active = aiAsyncTaskService.findActiveByKey(user.getId(),
                LearningConstants.AiTask.TYPE_ARTICLE_MATERIAL, null, idempotencyKey);
        if (active != null) return aiAsyncTaskService.toResponse(active);
        var task = aiAsyncTaskService.create(user.getId(), LearningConstants.AiTask.TYPE_ARTICLE_MATERIAL,
                "生成语境精读材料", null, null, null,
                LearningConstants.AiTask.EXECUTION_IMMEDIATE, null, null, 1, idempotencyKey, payload);
        return aiAsyncTaskService.toResponse(task);
    }

    /**
     * 查询 {@code listRecords} 相关业务。
     */
    @GetMapping
    @Operation(summary = "语境精读历史记录")
    public List<ArticleStudyResponse> listRecords(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) Long wordbookId,
            @RequestParam(defaultValue = LearningConstants.Article.DEFAULT_HISTORY_LIMIT_PARAM) Integer limit) {
        LearningUser user = authService.requireUser(authorization);
        return articleStudyService.listRecords(user.getId(), wordbookId, limit);
    }

    /**
     * 查询 {@code detail} 相关业务。
     */
    @GetMapping("/{recordId}")
    @Operation(summary = "语境精读记录详情")
    public ArticleStudyResponse detail(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long recordId) {
        LearningUser user = authService.requireUser(authorization);
        return articleStudyService.detail(user.getId(), recordId);
    }

    @PostMapping("/{recordId}/progress")
    @Operation(summary = "开始语境精读或切换学习阶段")
    public ArticleStudyResponse updateProgress(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long recordId,
            @Valid @RequestBody ArticleStudyProgressRequest request) {
        LearningUser user = authService.requireUser(authorization);
        return articleStudyService.updateProgress(user.getId(), recordId, request);
    }

    @PostMapping("/{recordId}/complete")
    @Operation(summary = "提交阅读检测并完成语境精读")
    public ArticleStudyResponse complete(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long recordId,
            @Valid @RequestBody ArticleStudyCompleteRequest request) {
        LearningUser user = authService.requireUser(authorization);
        return articleStudyService.complete(user.getId(), recordId, request);
    }
}
