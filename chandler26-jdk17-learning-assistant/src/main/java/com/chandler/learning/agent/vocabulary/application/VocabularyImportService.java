package com.chandler.learning.agent.vocabulary.application;

import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chandler.learning.agent.vocabulary.api.VocabularyImportBatchConfirmRequest;
import com.chandler.learning.agent.vocabulary.api.VocabularyImportEntryResponse;
import com.chandler.learning.agent.vocabulary.api.VocabularyImportEntryUpdateRequest;
import com.chandler.learning.agent.vocabulary.api.VocabularyImportPublishRequest;
import com.chandler.learning.agent.vocabulary.api.VocabularyImportMetadataUpdateRequest;
import com.chandler.learning.agent.vocabulary.api.VocabularyImportResponse;
import com.chandler.learning.agent.vocabulary.api.VocabularyImportPageResponse;
import com.chandler.learning.agent.vocabulary.api.VocabularyCatalogResponse;
import com.chandler.learning.agent.vocabulary.api.VocabularyMarkdownImportRequest;
import com.chandler.learning.agent.vocabulary.domain.LearningWordProgress;
import com.chandler.learning.agent.vocabulary.domain.LearningWordbook;
import com.chandler.learning.agent.vocabulary.domain.LearningWordbookEntry;
import com.chandler.learning.agent.vocabulary.domain.VocabularyCatalog;
import com.chandler.learning.agent.vocabulary.domain.VocabularyCatalogEntry;
import com.chandler.learning.agent.vocabulary.domain.VocabularyCatalogVersion;
import com.chandler.learning.agent.vocabulary.domain.VocabularyImportJob;
import com.chandler.learning.agent.system.domain.SystemLogType;
import com.chandler.learning.agent.exception.LearningAssistantException;
import com.chandler.learning.agent.vocabulary.infrastructure.LearningWordbookEntryMapper;
import com.chandler.learning.agent.vocabulary.infrastructure.LearningWordbookMapper;
import com.chandler.learning.agent.vocabulary.infrastructure.VocabularyCatalogEntryMapper;
import com.chandler.learning.agent.vocabulary.infrastructure.VocabularyCatalogMapper;
import com.chandler.learning.agent.vocabulary.infrastructure.VocabularyCatalogVersionMapper;
import com.chandler.learning.agent.vocabulary.infrastructure.VocabularyImportJobMapper;
import com.chandler.learning.agent.vocabulary.application.LearningWordProgressService;
import com.chandler.learning.agent.system.application.SystemLogService;
import com.chandler.learning.agent.identity.application.UserDisplayNameService;
import com.chandler.learning.agent.support.LearningConstants;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 词表导入、疑似断词审核和发布服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VocabularyImportService {

    private final MarkdownVocabularyParser markdownParser;
    private final VocabularyCatalogMapper catalogMapper;
    private final VocabularyCatalogVersionMapper versionMapper;
    private final VocabularyCatalogEntryMapper catalogEntryMapper;
    private final VocabularyImportJobMapper importJobMapper;
    private final LearningWordbookMapper wordbookMapper;
    private final LearningWordbookEntryMapper wordbookEntryMapper;
    private final LearningWordProgressService progressService;
    private final SystemLogService systemLogService;
    private final UserDisplayNameService userDisplayNameService;
    private final ObjectMapper objectMapper;

    /**
     * 导入 Markdown 并创建待审核版本。
     */
    @Transactional(rollbackFor = Exception.class)
    public VocabularyImportResponse importMarkdown(Long userId, VocabularyMarkdownImportRequest request) {
        List<MarkdownVocabularyParser.ParsedVocabulary> parsed = markdownParser.parse(request.getContent());
        LocalDateTime now = LocalDateTime.now();
        String sourceType = normalizeSourceType(request.getSourceType(), request.getExamType());

        VocabularyCatalog catalog = new VocabularyCatalog();
        catalog.setOwnerUserId(userId);
        catalog.setName(request.getCatalogName().trim());
        catalog.setLearningPurpose(trimToNull(request.getLearningPurpose()));
        catalog.setExamType(sourceType);
        catalog.setStatus(LearningConstants.VocabularyImport.CATALOG_STATUS_DRAFT);
        catalog.setVisibility(LearningConstants.VocabularyImport.VISIBILITY_PUBLIC);
        catalog.setDeleted(false);
        catalog.setCreateTime(now);
        catalog.setUpdateTime(now);
        catalogMapper.insert(catalog);

        int warningCount = (int) parsed.stream().filter(MarkdownVocabularyParser.ParsedVocabulary::suspicious).count();
        VocabularyCatalogVersion version = new VocabularyCatalogVersion();
        version.setCatalogId(catalog.getId());
        version.setVersionNo(LearningConstants.FIRST_SEQUENCE);
        version.setStatus(LearningConstants.VocabularyImport.VERSION_STATUS_REVIEWING);
        version.setSourceFormat(LearningConstants.VocabularyImport.FORMAT_MARKDOWN);
        version.setSourceFileName(trimToNull(request.getFileName()));
        version.setSourceHash(DigestUtil.sha256Hex(request.getContent()));
        version.setTotalCount(parsed.size());
        version.setWarningCount(warningCount);
        version.setReviewedWarningCount(LearningConstants.ZERO);
        version.setDeleted(false);
        version.setCreateTime(now);
        version.setUpdateTime(now);
        versionMapper.insert(version);

        VocabularyImportJob job = new VocabularyImportJob();
        job.setUserId(userId);
        job.setCatalogId(catalog.getId());
        job.setCatalogVersionId(version.getId());
        job.setSourceFormat(LearningConstants.VocabularyImport.FORMAT_MARKDOWN);
        job.setSourceFileName(trimToNull(request.getFileName()));
        job.setStatus(LearningConstants.VocabularyImport.STATUS_REVIEWING);
        job.setTotalCount(parsed.size());
        job.setWarningCount(warningCount);
        job.setReviewedWarningCount(LearningConstants.ZERO);
        job.setFinishedTime(now);
        job.setDeleted(false);
        job.setCreateTime(now);
        job.setUpdateTime(now);
        importJobMapper.insert(job);

        List<VocabularyCatalogEntry> batchList = new java.util.ArrayList<>();
        for (MarkdownVocabularyParser.ParsedVocabulary item : parsed) {
            VocabularyCatalogEntry entry = new VocabularyCatalogEntry();
            entry.setId(com.baomidou.mybatisplus.core.toolkit.IdWorker.getId());
            entry.setCatalogId(catalog.getId());
            entry.setCatalogVersionId(version.getId());
            entry.setSourceOrder(item.sourceOrder());
            entry.setOriginalTerm(item.originalTerm());
            entry.setNormalizedTerm(item.normalizedTerm());
            entry.setSuggestedTerm(item.suggestedTerm());
            entry.setPhonetic(trimToNull(item.phonetic()));
            entry.setDefinitionText(trimToNull(item.definition()));
            entry.setWarningCodes(writeJson(item.warnings()));
            entry.setSuspicious(item.suspicious());
            entry.setReviewStatus(item.suspicious()
                    ? LearningConstants.VocabularyImport.REVIEW_PENDING
                    : LearningConstants.VocabularyImport.REVIEW_NOT_REQUIRED);
            entry.setPublished(false);
            entry.setCreateBy(userId);
            entry.setUpdateBy(userId);
            entry.setCreateTime(now);
            entry.setUpdateTime(now);
            entry.setDeleted(false);
            entry.setVersion(LearningConstants.ZERO);

            batchList.add(entry);
            if (batchList.size() >= 500) {
                catalogEntryMapper.insertBatch(batchList);
                batchList.clear();
            }
        }
        if (!batchList.isEmpty()) {
            catalogEntryMapper.insertBatch(batchList);
        }

        systemLogService.record(userId, SystemLogType.VOCABULARY_IMPORT, "导入 Markdown 词表",
                catalog.getName() + "，共 " + parsed.size() + " 词，疑似断词 " + warningCount + " 个");
        log.info("用户「{}」导入了词表「{}」，共 {} 个词，其中 {} 个需要人工确认",
                userDisplayNameService.userName(userId), catalog.getName(), parsed.size(), warningCount);
        return detail(userId, job.getId(), warningCount > 0, null,
                LearningConstants.VocabularyImport.DEFAULT_PAGE,
                LearningConstants.VocabularyImport.DEFAULT_PAGE_SIZE);
    }

    /** 查询已发布的公共词本，供所有学习者创建计划。 */
    public List<VocabularyCatalogResponse> listPublicCatalogs() {
        List<VocabularyCatalog> catalogs = catalogMapper.selectList(new LambdaQueryWrapper<VocabularyCatalog>()
                .eq(VocabularyCatalog::getStatus, LearningConstants.VocabularyImport.CATALOG_STATUS_PUBLISHED)
                .eq(VocabularyCatalog::getVisibility, LearningConstants.VocabularyImport.VISIBILITY_PUBLIC)
                .eq(VocabularyCatalog::getDeleted, false)
                .orderByDesc(VocabularyCatalog::getUpdateTime));
        if (catalogs.isEmpty()) {
            return List.of();
        }
        List<Long> versionIds = catalogs.stream()
                .map(VocabularyCatalog::getLatestVersionId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, VocabularyCatalogVersion> versionMap = versionIds.isEmpty() ? Map.of()
                : versionMapper.selectBatchIds(versionIds).stream()
                .collect(Collectors.toMap(VocabularyCatalogVersion::getId, v -> v, (a, b) -> a));
        Map<Long, VocabularyImportJob> jobMap = versionIds.isEmpty() ? Map.of()
                : importJobMapper.selectList(new LambdaQueryWrapper<VocabularyImportJob>()
                .in(VocabularyImportJob::getCatalogVersionId, versionIds)
                .eq(VocabularyImportJob::getDeleted, false)
                .orderByDesc(VocabularyImportJob::getUpdateTime))
                .stream().collect(Collectors.toMap(VocabularyImportJob::getCatalogVersionId, j -> j, (a, b) -> a));

        return catalogs.stream().map(catalog -> {
            VocabularyCatalogVersion version = catalog.getLatestVersionId() == null ? null : versionMap.get(catalog.getLatestVersionId());
            VocabularyImportJob job = version == null ? null : jobMap.get(version.getId());
            VocabularyCatalogResponse response = new VocabularyCatalogResponse();
            response.setCatalogId(catalog.getId());
            response.setCatalogVersionId(catalog.getLatestVersionId());
            response.setJobId(job == null ? null : job.getId());
            response.setCatalogName(catalog.getName());
            response.setSourceType(catalog.getExamType());
            response.setLearningPurpose(catalog.getLearningPurpose());
            response.setStatus(catalog.getStatus());
            response.setTotalCount(version == null ? 0 : version.getTotalCount());
            response.setPublishedTime(version == null ? null : version.getPublishedTime());
            return response;
        }).toList();
    }

    /**
     * 查询导入任务，支持仅看疑似断词和关键词过滤。
     */
    public VocabularyImportResponse detail(Long userId, Long jobId, boolean warningOnly, String keyword,
                                           Integer page, Integer pageSize) {
        VocabularyImportJob job = requireJob(userId, jobId);
        VocabularyCatalog catalog = requireCatalog(userId, job.getCatalogId());
        int resolvedPage = Math.max(LearningConstants.VocabularyImport.DEFAULT_PAGE,
                page == null ? LearningConstants.VocabularyImport.DEFAULT_PAGE : page);
        int resolvedPageSize = Math.max(LearningConstants.FIRST_SEQUENCE,
                Math.min(pageSize == null ? LearningConstants.VocabularyImport.DEFAULT_PAGE_SIZE : pageSize,
                        LearningConstants.VocabularyImport.MAX_PAGE_SIZE));

        LambdaQueryWrapper<VocabularyCatalogEntry> wrapper = new LambdaQueryWrapper<VocabularyCatalogEntry>()
                .eq(VocabularyCatalogEntry::getCatalogVersionId, job.getCatalogVersionId())
                .eq(warningOnly, VocabularyCatalogEntry::getSuspicious, true)
                .eq(VocabularyCatalogEntry::getDeleted, false);

        if (StringUtils.hasText(keyword)) {
            String trimmed = keyword.trim();
            String normalized = normalize(trimmed);
            wrapper.and(w -> w.like(VocabularyCatalogEntry::getApprovedTerm, trimmed)
                    .or().like(VocabularyCatalogEntry::getOriginalTerm, trimmed)
                    .or().like(VocabularyCatalogEntry::getNormalizedTerm, normalized)
                    .or().like(VocabularyCatalogEntry::getDefinitionText, trimmed));
        }
        wrapper.orderByAsc(VocabularyCatalogEntry::getSourceOrder);

        Page<VocabularyCatalogEntry> pageResult = catalogEntryMapper.selectPage(
                new Page<>(resolvedPage, resolvedPageSize), wrapper);

        VocabularyImportResponse response = baseResponse(job, catalog);
        response.setPage(resolvedPage);
        response.setPageSize(resolvedPageSize);
        response.setFilteredTotal(pageResult.getTotal());
        response.setItems(pageResult.getRecords().stream().map(this::toEntryResponse).toList());
        return response;
    }

    /** 分页查询管理员导入历史；调用入口必须先完成管理员鉴权。 */
    public VocabularyImportPageResponse list(Long userId, Integer page, Integer pageSize) {
        int resolvedPage = Math.max(LearningConstants.VocabularyImport.DEFAULT_PAGE,
                page == null ? LearningConstants.VocabularyImport.DEFAULT_PAGE : page);
        int resolvedPageSize = Math.max(LearningConstants.FIRST_SEQUENCE,
                Math.min(pageSize == null ? LearningConstants.VocabularyImport.DEFAULT_PAGE_SIZE : pageSize,
                        LearningConstants.VocabularyImport.MAX_PAGE_SIZE));
        Page<VocabularyImportJob> pageResult = importJobMapper.selectPage(
                new Page<>(resolvedPage, resolvedPageSize),
                new LambdaQueryWrapper<VocabularyImportJob>()
                .eq(VocabularyImportJob::getDeleted, false)
                .orderByDesc(VocabularyImportJob::getUpdateTime));
        List<VocabularyImportJob> jobs = pageResult.getRecords();
        VocabularyImportPageResponse pageResponse = new VocabularyImportPageResponse();
        pageResponse.setTotal(pageResult.getTotal());
        pageResponse.setPage(resolvedPage);
        pageResponse.setPageSize(resolvedPageSize);
        if (jobs.isEmpty()) {
            pageResponse.setItems(List.of());
            return pageResponse;
        }
        Set<Long> catalogIds = jobs.stream().map(VocabularyImportJob::getCatalogId).collect(Collectors.toSet());
        Map<Long, VocabularyCatalog> catalogMap = catalogMapper.selectBatchIds(catalogIds).stream()
                .collect(Collectors.toMap(VocabularyCatalog::getId, c -> c, (a, b) -> a));
        pageResponse.setItems(jobs.stream().map(job -> {
            VocabularyCatalog catalog = catalogMap.get(job.getCatalogId());
            VocabularyImportResponse itemResponse = baseResponse(job, catalog != null ? catalog : new VocabularyCatalog());
            itemResponse.setItems(List.of());
            return itemResponse;
        }).toList());
        return pageResponse;
    }

    /**
     * 手工修改并确认一个疑似断词。
     */
    @Transactional(rollbackFor = Exception.class)
    public VocabularyImportEntryResponse updateEntry(Long userId, Long jobId, Long entryId,
                                                     VocabularyImportEntryUpdateRequest request) {
        VocabularyImportJob job = requireReviewingJob(userId, jobId);
        VocabularyCatalogEntry entry = requireEntry(job, entryId);
        String approvedTerm = request.getApprovedTerm().trim().replaceAll("\\s+", " ");
        entry.setApprovedTerm(approvedTerm);
        entry.setNormalizedTerm(normalize(approvedTerm));
        if (Boolean.TRUE.equals(entry.getSuspicious())) {
            entry.setReviewStatus(LearningConstants.VocabularyImport.REVIEW_CONFIRMED);
        }
        entry.setUpdateTime(LocalDateTime.now());
        catalogEntryMapper.updateById(entry);
        refreshReviewedCounts(job);
        return toEntryResponse(entry);
    }

    /**
     * 一次确认筛选出的疑似断词；默认采用系统建议，也可选择保留原词。
     */
    @Transactional(rollbackFor = Exception.class)
    public VocabularyImportResponse confirmWarnings(Long userId, Long jobId,
                                                    VocabularyImportBatchConfirmRequest request) {
        VocabularyImportJob job = requireReviewingJob(userId, jobId);
        VocabularyImportBatchConfirmRequest resolvedRequest = request == null
                ? new VocabularyImportBatchConfirmRequest()
                : request;
        List<VocabularyCatalogEntry> pending = catalogEntryMapper.selectList(
                new LambdaQueryWrapper<VocabularyCatalogEntry>()
                        .eq(VocabularyCatalogEntry::getCatalogVersionId, job.getCatalogVersionId())
                        .eq(VocabularyCatalogEntry::getSuspicious, true)
                        .eq(VocabularyCatalogEntry::getReviewStatus, LearningConstants.VocabularyImport.REVIEW_PENDING)
                        .eq(VocabularyCatalogEntry::getDeleted, false)
                        .in(resolvedRequest.getEntryIds() != null && !resolvedRequest.getEntryIds().isEmpty(),
                                VocabularyCatalogEntry::getId, resolvedRequest.getEntryIds()));
        boolean applySuggested = !Boolean.FALSE.equals(resolvedRequest.getApplySuggested());
        LocalDateTime now = LocalDateTime.now();
        for (VocabularyCatalogEntry entry : pending) {
            String approved = applySuggested && StringUtils.hasText(entry.getSuggestedTerm())
                    ? entry.getSuggestedTerm()
                    : entry.getOriginalTerm();
            entry.setApprovedTerm(approved);
            entry.setNormalizedTerm(normalize(approved));
            entry.setReviewStatus(LearningConstants.VocabularyImport.REVIEW_CONFIRMED);
            entry.setUpdateBy(userId);
            entry.setUpdateTime(now);
        }
        if (!pending.isEmpty()) {
            catalogEntryMapper.updateReviewBatch(pending);
        }
        refreshReviewedCounts(job);
        log.info("用户「{}」批量确认了词表导入任务 {} 中的 {} 个疑似断词",
                userDisplayNameService.userName(userId), jobId, pending.size());
        return detail(userId, jobId, true, null,
                LearningConstants.VocabularyImport.DEFAULT_PAGE,
                LearningConstants.VocabularyImport.DEFAULT_PAGE_SIZE);
    }

    /**
     * 删除词表导入记录（级联软删除任务、词表、版本和词条）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long userId, Long jobId) {
        VocabularyImportJob job = requireJob(userId, jobId);
        importJobMapper.deleteById(jobId);
        catalogMapper.deleteById(job.getCatalogId());
        versionMapper.deleteById(job.getCatalogVersionId());
        catalogEntryMapper.delete(new LambdaQueryWrapper<VocabularyCatalogEntry>()
                .eq(VocabularyCatalogEntry::getCatalogVersionId, job.getCatalogVersionId()));
        systemLogService.record(userId, SystemLogType.VOCABULARY_IMPORT, "删除导入词表记录",
                "任务ID: " + jobId + "，对应的公共词本及词条已软删除");
        log.info("用户「{}」删除了词表导入历史，任务ID = {}", userDisplayNameService.userName(userId), jobId);
    }

    /**
     * 更新未发布的词表导入任务的元数据。
     */
    @Transactional(rollbackFor = Exception.class)
    public VocabularyImportResponse updateMetadata(Long userId, Long jobId, VocabularyImportMetadataUpdateRequest request) {
        VocabularyImportJob job = requireReviewingJob(userId, jobId);
        VocabularyCatalog catalog = requireCatalog(userId, job.getCatalogId());
        LocalDateTime now = LocalDateTime.now();

        catalog.setName(request.getCatalogName().trim());
        catalog.setLearningPurpose(trimToNull(request.getLearningPurpose()));
        catalog.setExamType(normalizeSourceType(request.getSourceType(), null));
        catalog.setUpdateTime(now);
        catalogMapper.updateById(catalog);

        job.setUpdateTime(now);
        importJobMapper.updateById(job);

        log.info("用户「{}」更新了词表导入任务 {} 的元数据", userDisplayNameService.userName(userId), jobId);
        return detail(userId, jobId, false, null,
                LearningConstants.VocabularyImport.DEFAULT_PAGE,
                LearningConstants.VocabularyImport.DEFAULT_PAGE_SIZE);
    }

    /**
     * 发布已完成审核的词表，并把基础词条导入指定单词本；此过程不调用 AI。
     */
    @Transactional(rollbackFor = Exception.class)
    public VocabularyImportResponse publish(Long userId, Long jobId, VocabularyImportPublishRequest request) {
        VocabularyImportJob job = requireReviewingJob(userId, jobId);
        refreshReviewedCounts(job);
        if (!Objects.equals(job.getWarningCount(), job.getReviewedWarningCount())) {
            throw LearningAssistantException.badRequest(
                    LearningConstants.ErrorCode.VOCABULARY_IMPORT_NOT_REVIEWED,
                    "仍有 " + (value(job.getWarningCount()) - value(job.getReviewedWarningCount())) + " 个疑似断词未确认");
        }
        LearningWordbook wordbook = request == null || request.getWordbookId() == null
                ? null
                : requireWordbook(userId, request.getWordbookId());
        VocabularyCatalog catalog = requireCatalog(userId, job.getCatalogId());
        VocabularyCatalogVersion version = versionMapper.selectById(job.getCatalogVersionId());
        List<VocabularyCatalogEntry> entries = catalogEntryMapper.selectList(
                new LambdaQueryWrapper<VocabularyCatalogEntry>()
                        .eq(VocabularyCatalogEntry::getCatalogVersionId, job.getCatalogVersionId())
                        .eq(VocabularyCatalogEntry::getDeleted, false)
                        .orderByAsc(VocabularyCatalogEntry::getSourceOrder));
        Map<String, LearningWordProgress> progressByTerm = wordbook == null
                ? Map.of()
                : progressService.getOrCreateAll(userId,
                entries.stream().map(VocabularyCatalogEntry::effectiveTerm).toList());
        Map<String, LearningWordbookEntry> wordbookEntryByTerm = wordbook == null
                ? new LinkedHashMap<>()
                : wordbookEntryMapper.selectAllIncludingDeleted(wordbook.getId()).stream()
                .collect(Collectors.toMap(
                        LearningWordbookEntry::getNormalizedTerm,
                        entry -> entry,
                        (left, right) -> left,
                        LinkedHashMap::new));
        LocalDateTime now = LocalDateTime.now();
        int inserted = LearningConstants.ZERO;
        List<LearningWordbookEntry> newWordbookEntries = new java.util.ArrayList<>();
        List<LearningWordbookEntry> existingWordbookEntries = new java.util.ArrayList<>();
        List<Long> publishedEntryIds = new java.util.ArrayList<>();
        for (VocabularyCatalogEntry catalogEntry : entries) {
            if (wordbook != null) {
                String term = catalogEntry.effectiveTerm();
                String normalizedTerm = normalize(term);
                LearningWordProgress progress = progressByTerm.get(normalizedTerm);
                LearningWordbookEntry wordbookEntry = wordbookEntryByTerm.get(normalizedTerm);
                if (wordbookEntry == null) {
                    wordbookEntry = LearningWordbookEntry.createImported(
                            userId, wordbook.getId(), progress.getId(), catalogEntry.getId(), term,
                            normalizedTerm, basicSnapshot(catalogEntry, term), now);
                    newWordbookEntries.add(wordbookEntry);
                    wordbookEntryByTerm.put(normalizedTerm, wordbookEntry);
                    inserted++;
                } else {
                    wordbookEntry.setDeleted(false);
                    wordbookEntry.setProgressId(progress.getId());
                    wordbookEntry.setCatalogEntryId(catalogEntry.getId());
                    if (!StringUtils.hasText(wordbookEntry.getSnapshotParsedJson())) {
                        wordbookEntry.setSnapshotParsedJson(basicSnapshot(catalogEntry, term));
                        wordbookEntry.setSnapshotTime(now);
                    }
                    if (!StringUtils.hasText(wordbookEntry.getCardStatus())) {
                        wordbookEntry.setCardStatus(LearningConstants.VocabularyCard.STATUS_NOT_REQUIRED);
                    }
                    wordbookEntry.setUpdateBy(userId);
                    wordbookEntry.setUpdateTime(now);
                    existingWordbookEntries.add(wordbookEntry);
                }
            }
            publishedEntryIds.add(catalogEntry.getId());
        }
        for (List<LearningWordbookEntry> chunk : chunks(newWordbookEntries, 500)) {
            wordbookEntryMapper.insertBatch(chunk);
        }
        for (List<LearningWordbookEntry> chunk : chunks(existingWordbookEntries, 500)) {
            wordbookEntryMapper.updateImportedBatch(chunk);
        }
        for (List<Long> chunk : chunks(publishedEntryIds, 500)) {
            catalogEntryMapper.markPublishedBatch(chunk, now, userId);
        }

        version.setStatus(LearningConstants.VocabularyImport.VERSION_STATUS_PUBLISHED);
        version.setPublishedTime(now);
        version.setUpdateTime(now);
        versionMapper.updateById(version);
        catalog.setLatestVersionId(version.getId());
        catalog.setStatus(LearningConstants.VocabularyImport.CATALOG_STATUS_PUBLISHED);
        catalog.setUpdateTime(now);
        catalogMapper.updateById(catalog);
        job.setStatus(LearningConstants.VocabularyImport.STATUS_PUBLISHED);
        job.setFinishedTime(now);
        job.setUpdateTime(now);
        importJobMapper.updateById(job);

        String target = wordbook == null ? "公共词本" : "个人词本「" + wordbook.getName() + "」";
        systemLogService.record(userId, SystemLogType.VOCABULARY_IMPORT, "发布公共词本",
                catalog.getName() + " -> " + target + "，新增个人词条 " + inserted + " 词");
        log.info("用户「{}」发布了公共词本「{}」，目标 {}，词表共 {} 个词，新增个人词条 {} 个",
                userDisplayNameService.userName(userId), catalog.getName(), target, entries.size(), inserted);
        return detail(userId, jobId, false, null,
                LearningConstants.VocabularyImport.DEFAULT_PAGE,
                LearningConstants.VocabularyImport.DEFAULT_PAGE_SIZE);
    }

    private void refreshReviewedCounts(VocabularyImportJob job) {
        int reviewed = catalogEntryMapper.selectCount(new LambdaQueryWrapper<VocabularyCatalogEntry>()
                .eq(VocabularyCatalogEntry::getCatalogVersionId, job.getCatalogVersionId())
                .eq(VocabularyCatalogEntry::getSuspicious, true)
                .eq(VocabularyCatalogEntry::getReviewStatus, LearningConstants.VocabularyImport.REVIEW_CONFIRMED)
                .eq(VocabularyCatalogEntry::getDeleted, false)).intValue();
        job.setReviewedWarningCount(reviewed);
        job.setUpdateTime(LocalDateTime.now());
        importJobMapper.updateById(job);
        VocabularyCatalogVersion version = versionMapper.selectById(job.getCatalogVersionId());
        version.setReviewedWarningCount(reviewed);
        version.setUpdateTime(LocalDateTime.now());
        versionMapper.updateById(version);
    }

    /** 将批量写入拆成固定大小，避免单条 SQL 超过驱动或数据库限制。 */
    private <T> List<List<T>> chunks(List<T> source, int size) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        List<List<T>> result = new java.util.ArrayList<>();
        for (int offset = 0; offset < source.size(); offset += size) {
            result.add(source.subList(offset, Math.min(source.size(), offset + size)));
        }
        return result;
    }

    private VocabularyImportResponse baseResponse(VocabularyImportJob job, VocabularyCatalog catalog) {
        VocabularyImportResponse response = new VocabularyImportResponse();
        response.setJobId(job.getId());
        response.setCatalogId(job.getCatalogId());
        response.setCatalogVersionId(job.getCatalogVersionId());
        response.setImporterUserId(job.getUserId());
        response.setImporterName(userDisplayNameService.userName(job.getUserId()));
        response.setCatalogName(catalog.getName());
        response.setLearningPurpose(catalog.getLearningPurpose());
        response.setSourceType(catalog.getExamType());
        response.setFileName(job.getSourceFileName());
        response.setStatus(job.getStatus());
        response.setTotalCount(job.getTotalCount());
        response.setWarningCount(job.getWarningCount());
        response.setReviewedWarningCount(job.getReviewedWarningCount());
        response.setPendingWarningCount(Math.max(LearningConstants.ZERO,
                value(job.getWarningCount()) - value(job.getReviewedWarningCount())));
        response.setCreateTime(job.getCreateTime());
        return response;
    }

    private VocabularyImportEntryResponse toEntryResponse(VocabularyCatalogEntry entry) {
        VocabularyImportEntryResponse response = new VocabularyImportEntryResponse();
        response.setId(entry.getId());
        response.setSourceOrder(entry.getSourceOrder());
        response.setOriginalTerm(entry.getOriginalTerm());
        response.setSuggestedTerm(entry.getSuggestedTerm());
        response.setApprovedTerm(entry.getApprovedTerm());
        response.setEffectiveTerm(entry.effectiveTerm());
        response.setPhonetic(entry.getPhonetic());
        response.setDefinition(entry.getDefinitionText());
        response.setSuspicious(entry.getSuspicious());
        response.setReviewStatus(entry.getReviewStatus());
        response.setWarnings(readWarnings(entry.getWarningCodes()));
        return response;
    }

    private VocabularyImportJob requireJob(Long userId, Long identifier) {
        VocabularyImportJob job = importJobMapper.selectOne(new LambdaQueryWrapper<VocabularyImportJob>()
                .eq(VocabularyImportJob::getId, identifier)
                .eq(VocabularyImportJob::getDeleted, false)
                .last(LearningConstants.SQL_LIMIT_ONE));
        if (job == null) {
            job = importJobMapper.selectOne(new LambdaQueryWrapper<VocabularyImportJob>()
                    .eq(VocabularyImportJob::getCatalogVersionId, identifier)
                    .eq(VocabularyImportJob::getDeleted, false)
                    .orderByDesc(VocabularyImportJob::getUpdateTime)
                    .last(LearningConstants.SQL_LIMIT_ONE));
        }
        if (job == null) {
            job = importJobMapper.selectOne(new LambdaQueryWrapper<VocabularyImportJob>()
                    .eq(VocabularyImportJob::getCatalogId, identifier)
                    .eq(VocabularyImportJob::getDeleted, false)
                    .orderByDesc(VocabularyImportJob::getUpdateTime)
                    .last(LearningConstants.SQL_LIMIT_ONE));
        }
        if (job == null) {
            throw LearningAssistantException.notFound(
                    LearningConstants.ErrorCode.VOCABULARY_IMPORT_NOT_FOUND,
                    "词表导入任务不存在: " + identifier);
        }
        return job;
    }

    private VocabularyImportJob requireReviewingJob(Long userId, Long jobId) {
        VocabularyImportJob job = requireJob(userId, jobId);
        if (LearningConstants.VocabularyImport.STATUS_PUBLISHED.equals(job.getStatus())) {
            throw LearningAssistantException.badRequest(
                    LearningConstants.ErrorCode.VOCABULARY_IMPORT_ALREADY_PUBLISHED,
                    "词表已经发布，不能继续修改本次导入");
        }
        return job;
    }

    private VocabularyCatalog requireCatalog(Long userId, Long catalogId) {
        VocabularyCatalog catalog = catalogMapper.selectOne(new LambdaQueryWrapper<VocabularyCatalog>()
                .eq(VocabularyCatalog::getId, catalogId)
                .eq(VocabularyCatalog::getDeleted, false)
                .last(LearningConstants.SQL_LIMIT_ONE));
        if (catalog == null) {
            throw LearningAssistantException.notFound(
                    LearningConstants.ErrorCode.VOCABULARY_CATALOG_NOT_FOUND,
                    "词表不存在: " + catalogId);
        }
        return catalog;
    }

    private VocabularyCatalogEntry requireEntry(VocabularyImportJob job, Long entryId) {
        VocabularyCatalogEntry entry = catalogEntryMapper.selectOne(new LambdaQueryWrapper<VocabularyCatalogEntry>()
                .eq(VocabularyCatalogEntry::getId, entryId)
                .eq(VocabularyCatalogEntry::getCatalogVersionId, job.getCatalogVersionId())
                .eq(VocabularyCatalogEntry::getDeleted, false)
                .last(LearningConstants.SQL_LIMIT_ONE));
        if (entry == null) {
            throw LearningAssistantException.notFound(
                    LearningConstants.ErrorCode.VOCABULARY_IMPORT_NOT_FOUND,
                    "导入词条不存在: " + entryId);
        }
        return entry;
    }

    private LearningWordbook requireWordbook(Long userId, Long wordbookId) {
        LearningWordbook wordbook = wordbookMapper.selectOne(new LambdaQueryWrapper<LearningWordbook>()
                .eq(LearningWordbook::getId, wordbookId)
                .eq(LearningWordbook::getUserId, userId)
                .eq(LearningWordbook::getDeleted, false)
                .last(LearningConstants.SQL_LIMIT_ONE));
        if (wordbook == null) {
            throw LearningAssistantException.notFound(
                    LearningConstants.ErrorCode.WORDBOOK_NOT_FOUND,
                    "单词本不存在: " + wordbookId);
        }
        return wordbook;
    }

    private String basicSnapshot(VocabularyCatalogEntry entry, String term) {
        try {
            return objectMapper.writeValueAsString(new BasicVocabularyCard(
                    term,
                    entry.getPhonetic(),
                    List.of(new BasicDefinition(null, entry.getDefinitionText())),
                    true));
        } catch (Exception ex) {
            throw LearningAssistantException.system(
                    LearningConstants.ErrorCode.JSON_SERIALIZE_FAILED,
                    "导入词条基础快照生成失败",
                    ex);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw LearningAssistantException.system(
                    LearningConstants.ErrorCode.JSON_SERIALIZE_FAILED,
                    "导入警告序列化失败",
                    ex);
        }
    }

    private List<String> readWarnings(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(value, new TypeReference<List<String>>() {
            });
        } catch (Exception ex) {
            log.debug("导入警告 JSON 读取失败 value={} error={}", value, ex.getMessage());
            return List.of();
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private String normalizeSourceType(String sourceType, String legacyExamType) {
        String value = StringUtils.hasText(sourceType) ? sourceType.trim().toLowerCase(Locale.ROOT)
                : trimToNull(legacyExamType);
        if ("自考".equals(value) || LearningConstants.VocabularyImport.SOURCE_SELF_STUDY.equals(value)) {
            return LearningConstants.VocabularyImport.SOURCE_SELF_STUDY;
        }
        if ("四级".equals(value) || LearningConstants.VocabularyImport.SOURCE_CET4.equals(value)) {
            return LearningConstants.VocabularyImport.SOURCE_CET4;
        }
        if ("六级".equals(value) || LearningConstants.VocabularyImport.SOURCE_CET6.equals(value)) {
            return LearningConstants.VocabularyImport.SOURCE_CET6;
        }
        if ("雅思".equals(value) || LearningConstants.VocabularyImport.SOURCE_IELTS.equals(value)) {
            return LearningConstants.VocabularyImport.SOURCE_IELTS;
        }
        throw LearningAssistantException.badRequest(
                LearningConstants.ErrorCode.VOCABULARY_IMPORT_INVALID,
                "数据源类型仅支持自考、四级、六级或雅思");
    }

    private VocabularyCatalogResponse toCatalogResponse(VocabularyCatalog catalog) {
        VocabularyCatalogVersion version = catalog.getLatestVersionId() == null
                ? null : versionMapper.selectById(catalog.getLatestVersionId());
        VocabularyImportJob job = version == null ? null : importJobMapper.selectOne(
                new LambdaQueryWrapper<VocabularyImportJob>()
                        .eq(VocabularyImportJob::getCatalogVersionId, version.getId())
                        .eq(VocabularyImportJob::getDeleted, false)
                        .orderByDesc(VocabularyImportJob::getUpdateTime)
                        .last(LearningConstants.SQL_LIMIT_ONE));
        VocabularyCatalogResponse response = new VocabularyCatalogResponse();
        response.setCatalogId(catalog.getId());
        response.setCatalogVersionId(catalog.getLatestVersionId());
        response.setJobId(job == null ? null : job.getId());
        response.setCatalogName(catalog.getName());
        response.setSourceType(catalog.getExamType());
        response.setLearningPurpose(catalog.getLearningPurpose());
        response.setStatus(catalog.getStatus());
        response.setTotalCount(version == null ? 0 : version.getTotalCount());
        response.setPublishedTime(version == null ? null : version.getPublishedTime());
        return response;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private int value(Integer value) {
        return value == null ? LearningConstants.ZERO : value;
    }

    private record BasicVocabularyCard(String term, String phonetic, List<BasicDefinition> definitions,
                                       boolean importedBasicCard) {
    }

    private record BasicDefinition(String partOfSpeech, String meaning) {
    }
}
