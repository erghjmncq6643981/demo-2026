package com.chandler.learning.agent.vocabulary.api.controller;

import com.chandler.learning.agent.vocabulary.api.request.VocabularyCardGenerationRequest;
import com.chandler.learning.agent.vocabulary.api.response.VocabularyCardGenerationResponse;
import com.chandler.learning.agent.identity.domain.entity.LearningUser;
import com.chandler.learning.agent.security.CurrentUserContext;
import com.chandler.learning.agent.vocabulary.application.VocabularyCardBatchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 批量词卡任务查询与失败项重试接口。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/vocabulary-card-jobs")
@Tag(name = "批量词卡生成")
public class VocabularyCardGenerationController {

    private final CurrentUserContext currentUserContext;
    private final VocabularyCardBatchService cardBatchService;

    /** 批量词卡任务详情。 */
    @GetMapping("/{jobId}")
    @Operation(summary = "批量词卡任务详情")
    public VocabularyCardGenerationResponse detail(
            @PathVariable Long jobId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        LearningUser user = currentUserContext.requireUser();
        return cardBatchService.detail(user.getId(), jobId, page, pageSize);
    }

    /** 仅重试批量词卡任务中的失败词。 */
    @PostMapping("/{jobId}/retry")
    @Operation(summary = "仅重试批量词卡任务中的失败词")
    public VocabularyCardGenerationResponse retry(
            @PathVariable Long jobId,
            @RequestBody(required = false) VocabularyCardGenerationRequest request) {
        LearningUser user = currentUserContext.requireUser();
        return cardBatchService.retryFailed(user.getId(), jobId, request);
    }
}
