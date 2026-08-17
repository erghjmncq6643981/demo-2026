package com.chandler.learning.agent.controller.vocabulary;

import com.chandler.learning.agent.domain.dto.vocabulary.VocabularyCardGenerationRequest;
import com.chandler.learning.agent.domain.dto.vocabulary.VocabularyCardGenerationResponse;
import com.chandler.learning.agent.domain.entity.learning.LearningUser;
import com.chandler.learning.agent.service.learning.AuthService;
import com.chandler.learning.agent.service.vocabulary.VocabularyCardBatchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 批量词卡任务查询与失败项重试接口。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/vocabulary-card-jobs")
@Tag(name = "批量词卡生成")
public class VocabularyCardGenerationController {

    private final AuthService authService;
    private final VocabularyCardBatchService cardBatchService;

    @GetMapping("/{jobId}")
    @Operation(summary = "批量词卡任务详情")
    public VocabularyCardGenerationResponse detail(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long jobId) {
        LearningUser user = authService.requireUser(authorization);
        return cardBatchService.detail(user.getId(), jobId);
    }

    @PostMapping("/{jobId}/retry")
    @Operation(summary = "仅重试批量词卡任务中的失败词")
    public VocabularyCardGenerationResponse retry(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long jobId,
            @RequestBody(required = false) VocabularyCardGenerationRequest request) {
        LearningUser user = authService.requireUser(authorization);
        return cardBatchService.retryFailed(user.getId(), jobId, request);
    }
}
