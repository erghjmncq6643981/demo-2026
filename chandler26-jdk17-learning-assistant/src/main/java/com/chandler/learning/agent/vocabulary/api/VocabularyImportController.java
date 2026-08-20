package com.chandler.learning.agent.vocabulary.api;

import com.chandler.learning.agent.vocabulary.api.VocabularyImportBatchConfirmRequest;
import com.chandler.learning.agent.vocabulary.api.VocabularyImportEntryResponse;
import com.chandler.learning.agent.vocabulary.api.VocabularyImportEntryUpdateRequest;
import com.chandler.learning.agent.vocabulary.api.VocabularyImportPublishRequest;
import com.chandler.learning.agent.vocabulary.api.VocabularyImportMetadataUpdateRequest;
import com.chandler.learning.agent.vocabulary.api.VocabularyImportResponse;
import com.chandler.learning.agent.vocabulary.api.VocabularyCatalogResponse;
import com.chandler.learning.agent.vocabulary.api.VocabularyMarkdownImportRequest;
import com.chandler.learning.agent.identity.domain.LearningUser;
import com.chandler.learning.agent.identity.application.AuthService;
import com.chandler.learning.agent.vocabulary.application.VocabularyImportService;
import com.chandler.learning.agent.support.LearningConstants;
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
import org.springframework.web.bind.annotation.RequestHeader;
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

    private final AuthService authService;
    private final VocabularyImportService importService;

    @PostMapping("/markdown")
    @Operation(summary = "导入 Markdown 词表并生成审核预览")
    public VocabularyImportResponse importMarkdown(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody VocabularyMarkdownImportRequest request) {
        LearningUser user = authService.requireAdmin(authorization);
        return importService.importMarkdown(user.getId(), request);
    }

    @GetMapping
    @Operation(summary = "词表导入历史")
    public List<VocabularyImportResponse> list(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        LearningUser user = authService.requireAdmin(authorization);
        return importService.list(user.getId());
    }

    @GetMapping("/public")
    @Operation(summary = "查询已发布公共词本")
    public List<VocabularyCatalogResponse> publicCatalogs(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        authService.requireUser(authorization);
        return importService.listPublicCatalogs();
    }

    @GetMapping("/{jobId}")
    @Operation(summary = "导入任务和分页词条预览")
    public VocabularyImportResponse detail(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long jobId,
            @RequestParam(defaultValue = "false") Boolean warningOnly,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "100") Integer pageSize) {
        LearningUser user = authService.requireUser(authorization);
        return importService.detail(user.getId(), jobId, Boolean.TRUE.equals(warningOnly), keyword, page, pageSize);
    }

    @PutMapping("/{jobId}/entries/{entryId}")
    @Operation(summary = "手工修改并确认疑似断词")
    public VocabularyImportEntryResponse updateEntry(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long jobId,
            @PathVariable Long entryId,
            @Valid @RequestBody VocabularyImportEntryUpdateRequest request) {
        LearningUser user = authService.requireAdmin(authorization);
        return importService.updateEntry(user.getId(), jobId, entryId, request);
    }

    @PostMapping("/{jobId}/warnings/confirm")
    @Operation(summary = "批量确认疑似断词")
    public VocabularyImportResponse confirmWarnings(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long jobId,
            @RequestBody VocabularyImportBatchConfirmRequest request) {
        LearningUser user = authService.requireAdmin(authorization);
        return importService.confirmWarnings(user.getId(), jobId, request);
    }

    @PostMapping("/{jobId}/publish")
    @Operation(summary = "发布词表并导入指定单词本，不生成 AI 词卡")
    public VocabularyImportResponse publish(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long jobId,
            @Valid @RequestBody VocabularyImportPublishRequest request) {
        LearningUser user = authService.requireAdmin(authorization);
        return importService.publish(user.getId(), jobId, request);
    }

    @PutMapping("/{jobId}")
    @Operation(summary = "更新词表导入元数据")
    public VocabularyImportResponse updateMetadata(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long jobId,
            @Valid @RequestBody VocabularyImportMetadataUpdateRequest request) {
        LearningUser user = authService.requireAdmin(authorization);
        return importService.updateMetadata(user.getId(), jobId, request);
    }

    @DeleteMapping("/{jobId}")
    @Operation(summary = "删除词表导入记录")
    public void delete(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long jobId) {
        LearningUser user = authService.requireAdmin(authorization);
        importService.delete(user.getId(), jobId);
    }
}
