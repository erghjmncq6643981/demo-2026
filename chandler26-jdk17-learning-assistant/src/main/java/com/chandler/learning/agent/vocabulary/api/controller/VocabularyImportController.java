package com.chandler.learning.agent.vocabulary.api.controller;

import com.chandler.learning.agent.vocabulary.api.request.VocabularyImportBatchConfirmRequest;
import com.chandler.learning.agent.vocabulary.api.response.VocabularyImportEntryResponse;
import com.chandler.learning.agent.vocabulary.api.request.VocabularyImportEntryUpdateRequest;
import com.chandler.learning.agent.vocabulary.api.request.VocabularyImportPublishRequest;
import com.chandler.learning.agent.vocabulary.api.request.VocabularyImportMetadataUpdateRequest;
import com.chandler.learning.agent.vocabulary.api.response.VocabularyImportResponse;
import com.chandler.learning.agent.vocabulary.api.response.VocabularyImportPageResponse;
import com.chandler.learning.agent.vocabulary.api.response.VocabularyCatalogResponse;
import com.chandler.learning.agent.vocabulary.api.request.VocabularyMarkdownImportRequest;
import com.chandler.learning.agent.identity.domain.entity.LearningUser;
import com.chandler.learning.agent.security.CurrentUserContext;
import com.chandler.learning.agent.security.LearningPermission;
import com.chandler.learning.agent.security.RequirePermission;
import com.chandler.learning.agent.vocabulary.application.VocabularyImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 词表导入与疑似断词审核接口。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/vocabulary-imports")
@Tag(name = "词表导入")
public class VocabularyImportController {

    private final CurrentUserContext currentUserContext;
    private final VocabularyImportService importService;

    /** 导入 Markdown 词表并生成审核预览。 */
    @PostMapping("/markdown")
    @RequirePermission(LearningPermission.SYSTEM_ADMIN)
    @Operation(summary = "导入 Markdown 词表并生成审核预览")
    public VocabularyImportResponse importMarkdown(
            @Valid @RequestBody VocabularyMarkdownImportRequest request) {
        LearningUser user = currentUserContext.requireUser();
        return importService.importMarkdown(user.getId(), request);
    }

    /** 词表导入历史。 */
    @GetMapping
    @RequirePermission(LearningPermission.SYSTEM_ADMIN)
    @Operation(summary = "词表导入历史")
    public VocabularyImportPageResponse list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        LearningUser user = currentUserContext.requireUser();
        return importService.list(user.getId(), page, pageSize);
    }

    /** 查询已发布公共词本。 */
    @GetMapping("/public")
    @Operation(summary = "查询已发布公共词本")
    public List<VocabularyCatalogResponse> publicCatalogs() {
        currentUserContext.requireUser();
        return importService.listPublicCatalogs();
    }

    /** 导入任务和分页词条预览。 */
    @GetMapping("/{jobId}")
    @Operation(summary = "导入任务和分页词条预览")
    public VocabularyImportResponse detail(
            @PathVariable Long jobId,
            @RequestParam(defaultValue = "false") Boolean warningOnly,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "100") Integer pageSize) {
        LearningUser user = currentUserContext.requireUser();
        return importService.detail(user.getId(), jobId, Boolean.TRUE.equals(warningOnly), keyword, page, pageSize);
    }

    /** 手工修改并确认疑似断词。 */
    @PutMapping("/{jobId}/entries/{entryId}")
    @RequirePermission(LearningPermission.SYSTEM_ADMIN)
    @Operation(summary = "手工修改并确认疑似断词")
    public VocabularyImportEntryResponse updateEntry(
            @PathVariable Long jobId,
            @PathVariable Long entryId,
            @Valid @RequestBody VocabularyImportEntryUpdateRequest request) {
        LearningUser user = currentUserContext.requireUser();
        return importService.updateEntry(user.getId(), jobId, entryId, request);
    }

    /** 批量确认疑似断词。 */
    @PostMapping("/{jobId}/warnings/confirm")
    @RequirePermission(LearningPermission.SYSTEM_ADMIN)
    @Operation(summary = "批量确认疑似断词")
    public VocabularyImportResponse confirmWarnings(
            @PathVariable Long jobId,
            @RequestBody VocabularyImportBatchConfirmRequest request) {
        LearningUser user = currentUserContext.requireUser();
        return importService.confirmWarnings(user.getId(), jobId, request);
    }

    /** 发布词表并导入指定单词本，不生成 AI 词卡。 */
    @PostMapping("/{jobId}/publish")
    @RequirePermission(LearningPermission.SYSTEM_ADMIN)
    @Operation(summary = "发布词表并导入指定单词本，不生成 AI 词卡")
    public VocabularyImportResponse publish(
            @PathVariable Long jobId,
            @Valid @RequestBody VocabularyImportPublishRequest request) {
        LearningUser user = currentUserContext.requireUser();
        return importService.publish(user.getId(), jobId, request);
    }

    /** 更新词表导入元数据。 */
    @PutMapping("/{jobId}")
    @RequirePermission(LearningPermission.SYSTEM_ADMIN)
    @Operation(summary = "更新词表导入元数据")
    public VocabularyImportResponse updateMetadata(
            @PathVariable Long jobId,
            @Valid @RequestBody VocabularyImportMetadataUpdateRequest request) {
        LearningUser user = currentUserContext.requireUser();
        return importService.updateMetadata(user.getId(), jobId, request);
    }

    /** 删除词表导入记录。 */
    @DeleteMapping("/{jobId}")
    @RequirePermission(LearningPermission.SYSTEM_ADMIN)
    @Operation(summary = "删除词表导入记录")
    public void delete(
            @PathVariable Long jobId) {
        LearningUser user = currentUserContext.requireUser();
        importService.delete(user.getId(), jobId);
    }
}
