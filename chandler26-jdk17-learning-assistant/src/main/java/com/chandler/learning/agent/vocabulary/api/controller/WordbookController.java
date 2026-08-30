package com.chandler.learning.agent.vocabulary.api.controller;

import com.chandler.learning.agent.vocabulary.api.request.AddWordbookEntryRequest;
import com.chandler.learning.agent.identity.api.response.LearningActivityResponse;
import com.chandler.learning.agent.vocabulary.api.request.ReviewSubmitRequest;
import com.chandler.learning.agent.vocabulary.api.response.ReviewSubmitResponse;
import com.chandler.learning.agent.vocabulary.api.response.WordbookEntryResponse;
import com.chandler.learning.agent.vocabulary.api.response.WordbookEntryPageResponse;
import com.chandler.learning.agent.vocabulary.api.response.WordbookEntrySummaryResponse;
import com.chandler.learning.agent.vocabulary.api.request.WordbookEntryTransferRequest;
import com.chandler.learning.agent.vocabulary.api.request.WordbookEntryUpdateRequest;
import com.chandler.learning.agent.vocabulary.api.response.WordbookResponse;
import com.chandler.learning.agent.vocabulary.api.request.WordbookSaveRequest;
import com.chandler.learning.agent.identity.domain.entity.LearningUser;
import com.chandler.learning.agent.security.CurrentUserContext;
import com.chandler.learning.agent.vocabulary.application.WordbookService;
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

    /** 学习活跃图。 */
    @GetMapping("/activity")
    @Operation(summary = "学习活跃图")
    public LearningActivityResponse activity(
            @RequestParam(defaultValue = "180") Integer days) {
        LearningUser user = currentUserContext.requireUser();
        return wordbookService.activity(user.getId(), days == null ? 180 : days);
    }

    /** 单词本词条列表。 */
    @GetMapping("/wordbooks/{wordbookId}/entries")
    @Operation(summary = "单词本词条列表")
    public WordbookEntryPageResponse listEntries(
            @PathVariable Long wordbookId,
            @RequestParam(defaultValue = "false") Boolean dueOnly,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "30") Integer pageSize) {
        LearningUser user = currentUserContext.requireUser();
        return wordbookService.pageEntries(user.getId(), wordbookId, Boolean.TRUE.equals(dueOnly), status, keyword,
                page, pageSize);
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

    /** 为单词本词条生成或刷新 AI 词卡。 */
    @PostMapping("/wordbook-entries/{entryId}/generate-card")
    @Operation(summary = "为单词本词条生成或刷新 AI 词卡")
    public WordbookEntryResponse generateCard(
            @PathVariable Long entryId,
            @RequestParam(required = false, defaultValue = "false") boolean forceRefresh) {
        LearningUser user = currentUserContext.requireUser();
        return wordbookService.generateCard(user.getId(), entryId, forceRefresh);
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
