package com.chandler.learning.agent.vocabulary.api;

import com.chandler.learning.agent.vocabulary.api.AddWordbookEntryRequest;
import com.chandler.learning.agent.identity.api.LearningActivityResponse;
import com.chandler.learning.agent.vocabulary.api.ReviewSubmitRequest;
import com.chandler.learning.agent.vocabulary.api.ReviewSubmitResponse;
import com.chandler.learning.agent.vocabulary.api.WordbookEntryResponse;
import com.chandler.learning.agent.vocabulary.api.WordbookEntryTransferRequest;
import com.chandler.learning.agent.vocabulary.api.WordbookEntryUpdateRequest;
import com.chandler.learning.agent.vocabulary.api.WordbookResponse;
import com.chandler.learning.agent.vocabulary.api.WordbookSaveRequest;
import com.chandler.learning.agent.identity.domain.LearningUser;
import com.chandler.learning.agent.identity.application.AuthService;
import com.chandler.learning.agent.vocabulary.application.WordbookService;
import com.chandler.learning.agent.support.LearningConstants;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * WordbookController 类。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/learning")
@Tag(name = "单词本与复习")
public class WordbookController {

    private final AuthService authService;
    private final WordbookService wordbookService;

    /**
     * 查询 {@code listWordbooks} 相关业务。
     */
    @GetMapping("/wordbooks")
    @Operation(summary = "我的单词本列表")
    public List<WordbookResponse> listWordbooks(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        LearningUser user = authService.requireUser(authorization);
        return wordbookService.listWordbooks(user.getId());
    }

    /**
     * 创建或保存 {@code createWordbook} 相关业务。
     */
    @PostMapping("/wordbooks")
    @Operation(summary = "创建单词本")
    public WordbookResponse createWordbook(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody WordbookSaveRequest request) {
        LearningUser user = authService.requireUser(authorization);
        return wordbookService.createWordbook(user.getId(), request);
    }

    /**
     * 更新 {@code updateWordbook} 相关业务。
     */
    @PutMapping("/wordbooks/{wordbookId}")
    @Operation(summary = "更新单词本")
    public WordbookResponse updateWordbook(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long wordbookId,
            @Valid @RequestBody WordbookSaveRequest request) {
        LearningUser user = authService.requireUser(authorization);
        return wordbookService.updateWordbook(user.getId(), wordbookId, request);
    }

    /**
     * 更新 {@code deleteWordbook} 相关业务。
     */
    @DeleteMapping("/wordbooks/{wordbookId}")
    @Operation(summary = "删除单词本")
    public void deleteWordbook(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long wordbookId) {
        LearningUser user = authService.requireUser(authorization);
        wordbookService.deleteWordbook(user.getId(), wordbookId);
    }

    /**
     * 处理 {@code activity} 相关业务。
     */
    @GetMapping("/activity")
    @Operation(summary = "学习活跃图")
    public LearningActivityResponse activity(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(defaultValue = "180") Integer days) {
        LearningUser user = authService.requireUser(authorization);
        return wordbookService.activity(user.getId(), days == null ? 180 : days);
    }

    /**
     * 查询 {@code listEntries} 相关业务。
     */
    @GetMapping("/wordbooks/{wordbookId}/entries")
    @Operation(summary = "单词本词条列表")
    public WordbookEntryPageResponse listEntries(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long wordbookId,
            @RequestParam(defaultValue = "false") Boolean dueOnly,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "30") Integer pageSize) {
        LearningUser user = authService.requireUser(authorization);
        return wordbookService.pageEntries(user.getId(), wordbookId, Boolean.TRUE.equals(dueOnly), status, keyword,
                page, pageSize);
    }

    /**
     * 创建或保存 {@code addEntry} 相关业务。
     */
    @PostMapping("/wordbooks/{wordbookId}/entries")
    @Operation(summary = "加入单词本")
    public WordbookEntryResponse addEntry(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long wordbookId,
            @Valid @RequestBody AddWordbookEntryRequest request) {
        LearningUser user = authService.requireUser(authorization);
        return wordbookService.addEntry(user.getId(), wordbookId, request);
    }

    /**
     * 更新 {@code updateEntry} 相关业务。
     */
    @PutMapping("/wordbook-entries/{entryId}")
    @Operation(summary = "更新单词本词条笔记或状态")
    public WordbookEntryResponse updateEntry(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long entryId,
            @RequestBody WordbookEntryUpdateRequest request) {
        LearningUser user = authService.requireUser(authorization);
        return wordbookService.updateEntry(user.getId(), entryId, request);
    }

    @PostMapping("/wordbook-entries/{entryId}/generate-card")
    @Operation(summary = "为单词本词条生成或刷新 AI 词卡")
    public WordbookEntryResponse generateCard(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long entryId,
            @RequestParam(required = false, defaultValue = "false") boolean forceRefresh) {
        LearningUser user = authService.requireUser(authorization);
        return wordbookService.generateCard(user.getId(), entryId, forceRefresh);
    }

    /** 按需返回单个词条的完整词卡详情。 */
    @GetMapping("/wordbook-entries/{entryId}")
    @Operation(summary = "查看单词本词条详情")
    public WordbookEntryResponse detailEntry(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long entryId) {
        LearningUser user = authService.requireUser(authorization);
        return wordbookService.detailEntry(user.getId(), entryId);
    }

    /**
     * 更新 {@code deleteEntry} 相关业务。
     */
    @DeleteMapping("/wordbook-entries/{entryId}")
    @Operation(summary = "删除单词本词条")
    public void deleteEntry(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long entryId) {
        LearningUser user = authService.requireUser(authorization);
        wordbookService.deleteEntry(user.getId(), entryId);
    }

    /**
     * 处理 {@code transferEntry} 相关业务。
     */
    @PostMapping("/wordbook-entries/{entryId}/transfer")
    @Operation(summary = "复制或移动词条到其它单词本")
    public WordbookEntryResponse transferEntry(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long entryId,
            @Valid @RequestBody WordbookEntryTransferRequest request) {
        LearningUser user = authService.requireUser(authorization);
        return wordbookService.transferEntry(user.getId(), entryId, request);
    }

    /**
     * 处理 {@code dueEntries} 相关业务。
     */
    @GetMapping("/reviews/due")
    @Operation(summary = "待复习词条")
    public List<WordbookEntrySummaryResponse> dueEntries(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) Long wordbookId,
            @RequestParam(defaultValue = LearningConstants.Review.DUE_DEFAULT_LIMIT_PARAM) Integer limit) {
        LearningUser user = authService.requireUser(authorization);
        return wordbookService.listDueEntries(user.getId(), wordbookId, limit);
    }

    /**
     * 处理 {@code restartReviews} 相关业务。
     */
    @GetMapping("/reviews/restart")
    @Operation(summary = "重新生成本轮复习任务")
    public List<WordbookEntrySummaryResponse> restartReviews(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) Long wordbookId,
            @RequestParam(defaultValue = LearningConstants.Review.RESTART_DEFAULT_LIMIT_PARAM) Integer limit) {
        LearningUser user = authService.requireUser(authorization);
        return wordbookService.listRestartReviewEntries(user.getId(), wordbookId, limit);
    }

    /**
     * 处理 {@code submitReview} 相关业务。
     */
    @PostMapping("/reviews/{entryId}")
    @Operation(summary = "提交复习结果")
    public ReviewSubmitResponse submitReview(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long entryId,
            @RequestBody ReviewSubmitRequest request) {
        LearningUser user = authService.requireUser(authorization);
        return wordbookService.submitReview(user.getId(), entryId, request);
    }
}
