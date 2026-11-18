package com.chandler.learning.agent.vocabulary.api.controller;

import com.chandler.learning.agent.vocabulary.api.request.AddWordbookEntryRequest;
import com.chandler.learning.agent.vocabulary.api.request.RecentSearchReviewSubmitRequest;
import com.chandler.learning.agent.vocabulary.api.request.ReviewSubmitRequest;
import com.chandler.learning.agent.vocabulary.api.response.RecentSearchReviewQueueResponse;
import com.chandler.learning.agent.vocabulary.api.response.ReviewSubmitResponse;
import com.chandler.learning.agent.vocabulary.api.response.WordbookEntryResponse;
import com.chandler.learning.agent.task.api.response.AiAsyncTaskResponse;
import com.chandler.learning.agent.vocabulary.api.response.WordbookEntryPageResponse;
import com.chandler.learning.agent.vocabulary.api.response.WordbookEntrySummaryResponse;
import com.chandler.learning.agent.vocabulary.api.request.WordbookEntryTransferRequest;
import com.chandler.learning.agent.vocabulary.api.request.WordbookEntryUpdateRequest;
import com.chandler.learning.agent.vocabulary.api.response.WordbookResponse;
import com.chandler.learning.agent.vocabulary.api.request.WordbookSaveRequest;
import com.chandler.learning.agent.identity.domain.entity.LearningUser;
import com.chandler.learning.agent.security.CurrentUserContext;
import com.chandler.learning.agent.vocabulary.application.VocabularyLookupHistoryService;
import com.chandler.learning.agent.vocabulary.application.WordbookService;
import com.chandler.learning.agent.task.application.AiAsyncTaskService;
import com.chandler.learning.agent.vocabulary.domain.constant.ReviewConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 个人单词本接口控制器。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/learning")
@Tag(name = "单词本与复习")
public class WordbookController {

    private final CurrentUserContext currentUserContext;
    private final WordbookService wordbookService;
    private final VocabularyLookupHistoryService lookupHistoryService;
    private final AiAsyncTaskService aiAsyncTaskService;

    /** 我的单词本列表。 */
    @GetMapping("/wordbooks")
    @Operation(summary = "我的单词本列表")
    public List<WordbookResponse> listWordbooks() {
        LearningUser user = currentUserContext.requireUser();
        return wordbookService.listWordbooks(user.getId());
    }

    /** 创建单词本。 */
    @PostMapping("/wordbooks")
    @Operation(summary = "创建单词本")
    public WordbookResponse createWordbook(
            @Valid @RequestBody WordbookSaveRequest request) {
        LearningUser user = currentUserContext.requireUser();
        return wordbookService.createWordbook(user.getId(), request);
    }

    /** 更新单词本。 */
    @PutMapping("/wordbooks/{wordbookId}")
    @Operation(summary = "更新单词本")
    public WordbookResponse updateWordbook(
            @PathVariable Long wordbookId,
            @Valid @RequestBody WordbookSaveRequest request) {
        LearningUser user = currentUserContext.requireUser();
        return wordbookService.updateWordbook(user.getId(), wordbookId, request);
    }

    /** 删除单词本。 */
    @DeleteMapping("/wordbooks/{wordbookId}")
    @Operation(summary = "删除单词本")
    public void deleteWordbook(
            @PathVariable Long wordbookId) {
        LearningUser user = currentUserContext.requireUser();
        wordbookService.deleteWordbook(user.getId(), wordbookId);
    }

    /** 单词本词条列表。 */
    @GetMapping("/wordbooks/{wordbookId}/entries")
    @Operation(summary = "单词本词条列表")
    public WordbookEntryPageResponse listEntries(
            @PathVariable Long wordbookId,
            @RequestParam(defaultValue = "false") Boolean dueOnly,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String cardStatus,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "30") Integer pageSize) {
        LearningUser user = currentUserContext.requireUser();
        return wordbookService.pageEntries(user.getId(), wordbookId, Boolean.TRUE.equals(dueOnly), status, cardStatus,
                keyword, page, pageSize);
    }

    /** 加入单词本。 */
    @PostMapping("/wordbooks/{wordbookId}/entries")
    @Operation(summary = "加入单词本")
    public WordbookEntryResponse addEntry(
            @PathVariable Long wordbookId,
            @Valid @RequestBody AddWordbookEntryRequest request) {
        LearningUser user = currentUserContext.requireUser();
        return wordbookService.addEntry(user.getId(), wordbookId, request);
    }

    /** 更新单词本词条笔记或状态。 */
    @PutMapping("/wordbook-entries/{entryId}")
    @Operation(summary = "更新单词本词条笔记或状态")
    public WordbookEntryResponse updateEntry(
            @PathVariable Long entryId,
            @RequestBody WordbookEntryUpdateRequest request) {
        LearningUser user = currentUserContext.requireUser();
        return wordbookService.updateEntry(user.getId(), entryId, request);
    }

    /** 兼容旧路径：提交 AI 词卡任务，不在 HTTP 请求中等待模型返回。 */
    @PostMapping("/wordbook-entries/{entryId}/generate-card")
    @Operation(summary = "提交 AI 词卡生成任务（兼容路径）")
    public AiAsyncTaskResponse generateCard(
            @PathVariable Long entryId,
            @RequestParam(required = false, defaultValue = "false") boolean forceRefresh) {
        LearningUser user = currentUserContext.requireUser();
        return aiAsyncTaskService.toResponse(
                wordbookService.submitCardGenerationTask(user.getId(), entryId, forceRefresh));
    }

    /** 提交单个词条的异步 AI 词卡生成任务。 */
    @PostMapping("/wordbook-entries/{entryId}/generate-card/async")
    @Operation(summary = "提交异步 AI 词卡生成任务")
    public AiAsyncTaskResponse generateCardAsync(
            @PathVariable Long entryId,
            @RequestParam(required = false, defaultValue = "true") boolean forceRefresh) {
        LearningUser user = currentUserContext.requireUser();
        return aiAsyncTaskService.toResponse(
                wordbookService.submitCardGenerationTask(user.getId(), entryId, forceRefresh));
    }

    /** 按需返回单个词条的完整词卡详情。 */
    @GetMapping("/wordbook-entries/{entryId}")
    @Operation(summary = "查看单词本词条详情")
    public WordbookEntryResponse detailEntry(
            @PathVariable Long entryId) {
        LearningUser user = currentUserContext.requireUser();
        return wordbookService.detailEntry(user.getId(), entryId);
    }

    /** 删除单词本词条。 */
    @DeleteMapping("/wordbook-entries/{entryId}")
    @Operation(summary = "删除单词本词条")
    public void deleteEntry(
            @PathVariable Long entryId) {
        LearningUser user = currentUserContext.requireUser();
        wordbookService.deleteEntry(user.getId(), entryId);
    }

    /** 复制或移动词条到其它单词本。 */
    @PostMapping("/wordbook-entries/{entryId}/transfer")
    @Operation(summary = "复制或移动词条到其它单词本")
    public WordbookEntryResponse transferEntry(
            @PathVariable Long entryId,
            @Valid @RequestBody WordbookEntryTransferRequest request) {
        LearningUser user = currentUserContext.requireUser();
        return wordbookService.transferEntry(user.getId(), entryId, request);
    }

    /** 待复习词条。 */
    @GetMapping("/reviews/due")
    @Operation(summary = "待复习词条")
    public List<WordbookEntrySummaryResponse> dueEntries(
            @RequestParam(required = false) Long wordbookId,
            @RequestParam(defaultValue = ReviewConstants.DUE_DEFAULT_LIMIT_PARAM) Integer limit) {
        LearningUser user = currentUserContext.requireUser();
        return wordbookService.listDueEntries(user.getId(), wordbookId, limit);
    }

    /** 重新生成本轮复习任务。 */
    @GetMapping("/reviews/restart")
    @Operation(summary = "重新生成本轮复习任务")
    public List<WordbookEntrySummaryResponse> restartReviews(
            @RequestParam(required = false) Long wordbookId,
            @RequestParam(defaultValue = ReviewConstants.RESTART_DEFAULT_LIMIT_PARAM) Integer limit) {
        LearningUser user = currentUserContext.requireUser();
        return wordbookService.listRestartReviewEntries(user.getId(), wordbookId, limit);
    }

    /** 获取近期搜索词汇轻量复习队列（今天+昨天优先，少于 5 词自动兜底最近 20 词）。 */
    @GetMapping("/reviews/recent-searches")
    @Operation(summary = "获取近期查词轻量复习队列")
    public RecentSearchReviewQueueResponse recentSearchReviews(
            @RequestParam(defaultValue = "2") Integer days,
            @RequestParam(defaultValue = "5") Integer minCount,
            @RequestParam(defaultValue = "20") Integer fallbackLimit) {
        LearningUser user = currentUserContext.requireUser();
        return lookupHistoryService.listRecentSearchReviewQueue(user.getId(), days, minCount, fallbackLimit);
    }

    /** 提交近期查词轻量复习结果（已入词本则联动更新艾宾浩斯排期，未入词本则记录足迹与活跃度）。 */
    @PostMapping("/reviews/recent-searches/submit")
    @Operation(summary = "提交近期查词轻量复习结果")
    public ReviewSubmitResponse submitRecentSearchReview(
            @Valid @RequestBody RecentSearchReviewSubmitRequest request) {
        LearningUser user = currentUserContext.requireUser();
        Long resolvedEntryId = request.getEntryId();
        if (resolvedEntryId == null) {
            resolvedEntryId = lookupHistoryService.findOwnedEntryIdByTerm(user.getId(), request.getTerm());
        }
        if (resolvedEntryId != null) {
            ReviewSubmitRequest reviewReq = new ReviewSubmitRequest();
            reviewReq.setResult(request.getResult());
            reviewReq.setAssessmentType(request.getReviewAction());
            reviewReq.setDurationSeconds(request.getDurationSeconds());
            ReviewSubmitResponse response = wordbookService.submitReview(user.getId(), resolvedEntryId, reviewReq);
            lookupHistoryService.markReviewed(user.getId(), request.getTerm());
            return response;
        }
        return lookupHistoryService.submitLookupOnlyReview(user.getId(), request);
    }

    /** 提交复习结果。 */
    @PostMapping("/reviews/{entryId}")
    @Operation(summary = "提交复习结果")
    public ReviewSubmitResponse submitReview(
            @PathVariable Long entryId,
            @RequestBody ReviewSubmitRequest request) {
        LearningUser user = currentUserContext.requireUser();
        return wordbookService.submitReview(user.getId(), entryId, request);
    }
}
