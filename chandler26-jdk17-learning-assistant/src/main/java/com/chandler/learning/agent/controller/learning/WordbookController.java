package com.chandler.learning.agent.controller.learning;

import com.chandler.learning.agent.domain.dto.learning.AddWordbookEntryRequest;
import com.chandler.learning.agent.domain.dto.learning.LearningActivityResponse;
import com.chandler.learning.agent.domain.dto.learning.ReviewSubmitRequest;
import com.chandler.learning.agent.domain.dto.learning.ReviewSubmitResponse;
import com.chandler.learning.agent.domain.dto.learning.WordbookEntryResponse;
import com.chandler.learning.agent.domain.dto.learning.WordbookEntryUpdateRequest;
import com.chandler.learning.agent.domain.dto.learning.WordbookResponse;
import com.chandler.learning.agent.domain.dto.learning.WordbookSaveRequest;
import com.chandler.learning.agent.domain.entity.learning.LearningUser;
import com.chandler.learning.agent.service.learning.AuthService;
import com.chandler.learning.agent.service.learning.WordbookService;
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

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/learning")
@Tag(name = "词书与复习")
public class WordbookController {

    private final AuthService authService;
    private final WordbookService wordbookService;

    @GetMapping("/wordbooks")
    @Operation(summary = "我的词书列表")
    public List<WordbookResponse> listWordbooks(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        LearningUser user = authService.requireUser(authorization);
        return wordbookService.listWordbooks(user.getId());
    }

    @PostMapping("/wordbooks")
    @Operation(summary = "创建词书")
    public WordbookResponse createWordbook(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody WordbookSaveRequest request) {
        LearningUser user = authService.requireUser(authorization);
        return wordbookService.createWordbook(user.getId(), request);
    }

    @PutMapping("/wordbooks/{wordbookId}")
    @Operation(summary = "更新词书")
    public WordbookResponse updateWordbook(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long wordbookId,
            @Valid @RequestBody WordbookSaveRequest request) {
        LearningUser user = authService.requireUser(authorization);
        return wordbookService.updateWordbook(user.getId(), wordbookId, request);
    }

    @DeleteMapping("/wordbooks/{wordbookId}")
    @Operation(summary = "删除词书")
    public void deleteWordbook(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long wordbookId) {
        LearningUser user = authService.requireUser(authorization);
        wordbookService.deleteWordbook(user.getId(), wordbookId);
    }

    @GetMapping("/activity")
    @Operation(summary = "学习活跃图")
    public LearningActivityResponse activity(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(defaultValue = "180") Integer days) {
        LearningUser user = authService.requireUser(authorization);
        return wordbookService.activity(user.getId(), days == null ? 180 : days);
    }

    @GetMapping("/wordbooks/{wordbookId}/entries")
    @Operation(summary = "词书词条列表")
    public List<WordbookEntryResponse> listEntries(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long wordbookId,
            @RequestParam(defaultValue = "false") Boolean dueOnly,
            @RequestParam(required = false) String status) {
        LearningUser user = authService.requireUser(authorization);
        return wordbookService.listEntries(user.getId(), wordbookId, Boolean.TRUE.equals(dueOnly), status);
    }

    @PostMapping("/wordbooks/{wordbookId}/entries")
    @Operation(summary = "加入词书")
    public WordbookEntryResponse addEntry(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long wordbookId,
            @Valid @RequestBody AddWordbookEntryRequest request) {
        LearningUser user = authService.requireUser(authorization);
        return wordbookService.addEntry(user.getId(), wordbookId, request);
    }

    @PutMapping("/wordbook-entries/{entryId}")
    @Operation(summary = "更新词书词条笔记或状态")
    public WordbookEntryResponse updateEntry(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long entryId,
            @RequestBody WordbookEntryUpdateRequest request) {
        LearningUser user = authService.requireUser(authorization);
        return wordbookService.updateEntry(user.getId(), entryId, request);
    }

    @DeleteMapping("/wordbook-entries/{entryId}")
    @Operation(summary = "删除词书词条")
    public void deleteEntry(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long entryId) {
        LearningUser user = authService.requireUser(authorization);
        wordbookService.deleteEntry(user.getId(), entryId);
    }

    @GetMapping("/reviews/due")
    @Operation(summary = "待复习词条")
    public List<WordbookEntryResponse> dueEntries(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) Long wordbookId) {
        LearningUser user = authService.requireUser(authorization);
        return wordbookService.listDueEntries(user.getId(), wordbookId);
    }

    @GetMapping("/reviews/restart")
    @Operation(summary = "重新生成本轮复习任务")
    public List<WordbookEntryResponse> restartReviews(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) Long wordbookId,
            @RequestParam(defaultValue = LearningConstants.Review.RESTART_DEFAULT_LIMIT_PARAM) Integer limit) {
        LearningUser user = authService.requireUser(authorization);
        return wordbookService.listRestartReviewEntries(user.getId(), wordbookId, limit);
    }

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
