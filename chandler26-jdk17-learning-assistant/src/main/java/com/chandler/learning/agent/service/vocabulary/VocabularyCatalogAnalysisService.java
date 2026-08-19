package com.chandler.learning.agent.service.vocabulary;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.chandler.learning.agent.domain.dto.AgentChatRequest;
import com.chandler.learning.agent.domain.dto.AgentChatResponse;
import com.chandler.learning.agent.domain.dto.vocabulary.VocabularyCatalogAnalysisRequest;
import com.chandler.learning.agent.domain.dto.vocabulary.VocabularyCatalogAnalysisResponse;
import com.chandler.learning.agent.domain.entity.learning.AiAsyncTask;
import com.chandler.learning.agent.domain.entity.vocabulary.VocabularyCatalog;
import com.chandler.learning.agent.domain.entity.vocabulary.VocabularyCatalogAnalysisBatch;
import com.chandler.learning.agent.domain.entity.vocabulary.VocabularyCatalogAnalysisJob;
import com.chandler.learning.agent.domain.entity.vocabulary.VocabularyCatalogEntry;
import com.chandler.learning.agent.domain.entity.vocabulary.VocabularyCatalogEntryAnalysis;
import com.chandler.learning.agent.domain.entity.vocabulary.VocabularyCatalogVersion;
import com.chandler.learning.agent.domain.enums.AiInvocationScene;
import com.chandler.learning.agent.domain.enums.SystemLogType;
import com.chandler.learning.agent.exception.LearningAssistantException;
import com.chandler.learning.agent.mapper.vocabulary.VocabularyCatalogAnalysisBatchMapper;
import com.chandler.learning.agent.mapper.vocabulary.VocabularyCatalogAnalysisJobMapper;
import com.chandler.learning.agent.mapper.vocabulary.VocabularyCatalogEntryAnalysisMapper;
import com.chandler.learning.agent.mapper.vocabulary.VocabularyCatalogEntryMapper;
import com.chandler.learning.agent.mapper.vocabulary.VocabularyCatalogMapper;
import com.chandler.learning.agent.mapper.vocabulary.VocabularyCatalogVersionMapper;
import com.chandler.learning.agent.service.AiChatService;
import com.chandler.learning.agent.service.learning.AiAsyncTaskService;
import com.chandler.learning.agent.service.learning.SystemLogService;
import com.chandler.learning.agent.service.learning.UserDisplayNameService;
import com.chandler.learning.agent.support.LearningConstants;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;

/**
 * 公共词本语义索引分析服务。
 * <p>
 * 词本分析是一次性的基础加工；场景生成只读取这里的结果，不在每次场景生成时重复分析。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VocabularyCatalogAnalysisService {

    /** 单次 AI 返回可能只覆盖部分词条，未覆盖项留待下次分析。 */
    record AnalysisParseResult(List<VocabularyCatalogEntryAnalysis> analyses,
                               List<Long> unresolvedEntryIds) {
    }

    private final VocabularyCatalogMapper catalogMapper;
    private final VocabularyCatalogVersionMapper versionMapper;
    private final VocabularyCatalogEntryMapper entryMapper;
    private final VocabularyCatalogAnalysisJobMapper jobMapper;
    private final VocabularyCatalogAnalysisBatchMapper batchMapper;
    private final VocabularyCatalogEntryAnalysisMapper entryAnalysisMapper;
    private final AiAsyncTaskService asyncTaskService;
    private final AiChatService aiChatService;
    private final SystemLogService systemLogService;
    private final UserDisplayNameService userDisplayNameService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    /** 为已发布词本创建一次可重试的语义分析任务。 */
    public VocabularyCatalogAnalysisResponse trigger(Long userId, Long catalogVersionId,
                                                      VocabularyCatalogAnalysisRequest request) {
        VocabularyCatalogVersion version = requirePublishedVersion(catalogVersionId);
        requireCatalogReadable(userId, version.getCatalogId());
        VocabularyCatalogAnalysisRequest resolved = request == null
                ? new VocabularyCatalogAnalysisRequest() : request;
        int batchSize = resolveBatchSize(resolved.getBatchSize());
        boolean force = Boolean.TRUE.equals(resolved.getForce());
        VocabularyCatalogAnalysisJob latest = latestJob(catalogVersionId);
        if (!force && latest != null) {
            if (List.of(LearningConstants.VocabularyAnalysis.STATUS_PENDING,
                    LearningConstants.VocabularyAnalysis.STATUS_RUNNING).contains(latest.getStatus())) {
                return toResponse(latest);
            }
            if (List.of(LearningConstants.VocabularyAnalysis.STATUS_COMPLETED,
                    LearningConstants.VocabularyAnalysis.STATUS_PARTIAL_FAILED,
                    LearningConstants.VocabularyAnalysis.STATUS_FAILED).contains(latest.getStatus())
                    && !hasUnanalyzedEntries(version.getId())) {
                return toResponse(latest);
            }
        }

        List<VocabularyCatalogEntry> entries = force
                ? entryMapper.selectList(new LambdaQueryWrapper<VocabularyCatalogEntry>()
                .eq(VocabularyCatalogEntry::getCatalogVersionId, catalogVersionId)
                .eq(VocabularyCatalogEntry::getPublished, true)
                .eq(VocabularyCatalogEntry::getDeleted, false)
                .orderByAsc(VocabularyCatalogEntry::getSourceOrder))
                : entryMapper.selectUnanalyzedPublished(catalogVersionId);
        if (entries.isEmpty() && latest != null) {
            return toResponse(latest);
        }
        if (entries.isEmpty()) {
            throw LearningAssistantException.badRequest(LearningConstants.ErrorCode.LEARNING_PLAN_NO_WORDS,
                    "公共词本中没有可分析词条");
        }

        List<VocabularyCatalogEntry> entriesToAnalyze = List.copyOf(entries);
        VocabularyCatalog catalog = catalogMapper.selectById(version.getCatalogId());
        VocabularyCatalogAnalysisJob job;
        try {
            job = transactionTemplate.execute(status -> createJob(
                    userId, catalog, version, entriesToAnalyze, batchSize, resolved));
        } catch (DuplicateKeyException ex) {
            VocabularyCatalogAnalysisJob concurrent = latestJob(catalogVersionId);
            if (concurrent != null) {
                return toResponse(concurrent);
            }
            throw ex;
        }
        if (job == null) {
            throw LearningAssistantException.badRequest(LearningConstants.ErrorCode.SYSTEM_UNEXPECTED,
                    "创建词本分析任务失败");
        }
        systemLogService.record(userId, SystemLogType.AI, "创建公共词本关联分析任务",
                catalog.getName() + "，共 " + entriesToAnalyze.size() + " 个词条");
        log.info("用户「{}」为公共词本「{}」创建关联分析任务，共 {} 个词条、批大小 {}",
                userDisplayNameService.userName(userId), catalog.getName(), entriesToAnalyze.size(), batchSize);
        return toResponse(jobMapper.selectById(job.getId()));
    }

    /** 查询当前词本版本最新分析任务。 */
    public VocabularyCatalogAnalysisResponse detail(Long userId, Long catalogVersionId) {
        VocabularyCatalogVersion version = requirePublishedVersion(catalogVersionId);
        requireCatalogReadable(userId, version.getCatalogId());
        VocabularyCatalogAnalysisJob job = latestJob(catalogVersionId);
        if (job == null) {
            VocabularyCatalogAnalysisResponse response = new VocabularyCatalogAnalysisResponse();
            response.setCatalogId(version.getCatalogId());
            response.setCatalogVersionId(version.getId());
            response.setStatus("not_started");
            fillCoverage(response, version.getId());
            return response;
        }
        return toResponse(job);
    }

    /** 异步 Worker 执行任务，AI 调用发生在事务之外。 */
    public void executeJob(Long userId, Long jobId, Long modelConfigId) {
        VocabularyCatalogAnalysisJob job = jobMapper.selectById(jobId);
        if (job == null || !job.getUserId().equals(userId)) {
            throw LearningAssistantException.notFound(LearningConstants.ErrorCode.AI_ASYNC_TASK_NOT_FOUND);
        }
        int claimed = jobMapper.update(null, new LambdaUpdateWrapper<VocabularyCatalogAnalysisJob>()
                .eq(VocabularyCatalogAnalysisJob::getId, jobId)
                .in(VocabularyCatalogAnalysisJob::getStatus, List.of(
                        LearningConstants.VocabularyAnalysis.STATUS_PENDING,
                        LearningConstants.VocabularyAnalysis.STATUS_RUNNING,
                        LearningConstants.VocabularyAnalysis.STATUS_PARTIAL_FAILED,
                        LearningConstants.VocabularyAnalysis.STATUS_FAILED,
                        LearningConstants.VocabularyAnalysis.STATUS_CANCELLED))
                .set(VocabularyCatalogAnalysisJob::getStatus, LearningConstants.VocabularyAnalysis.STATUS_RUNNING)
                .set(VocabularyCatalogAnalysisJob::getFailedCount, LearningConstants.ZERO)
                .set(VocabularyCatalogAnalysisJob::getStartedTime, LocalDateTime.now())
                .set(VocabularyCatalogAnalysisJob::getFinishedTime, null)
                .set(VocabularyCatalogAnalysisJob::getUpdateTime, LocalDateTime.now()));
        if (claimed == LearningConstants.ZERO) {
            return;
        }

        job = jobMapper.selectById(jobId);
        if (isAnalysisCancelled(job)) {
            markAnalysisCancelled(job, null, value(job.getSuccessCount()), value(job.getFailedCount()));
            return;
        }
        List<VocabularyCatalogAnalysisBatch> batches = batchMapper.selectList(
                new LambdaQueryWrapper<VocabularyCatalogAnalysisBatch>()
                        .eq(VocabularyCatalogAnalysisBatch::getJobId, jobId)
                        .in(VocabularyCatalogAnalysisBatch::getStatus, List.of(
                                LearningConstants.VocabularyAnalysis.ITEM_PENDING,
                                LearningConstants.VocabularyAnalysis.ITEM_RUNNING,
                                LearningConstants.VocabularyAnalysis.ITEM_FAILED))
                        .eq(VocabularyCatalogAnalysisBatch::getDeleted, false)
                        .orderByAsc(VocabularyCatalogAnalysisBatch::getBatchNo));
        int successCount = value(job.getSuccessCount());
        int failedCount = value(job.getFailedCount());
        for (VocabularyCatalogAnalysisBatch batch : batches) {
            if (isAnalysisCancelled(job)) {
                markAnalysisCancelled(job, batch, successCount, failedCount);
                return;
            }
            markBatchRunning(batch, userId);
            List<Long> unresolvedEntryIds = new ArrayList<>();
            int batchSuccessCount = LearningConstants.ZERO;
            int batchFailedCount = LearningConstants.ZERO;
            try {
                List<Long> entryIds = readIds(batch.getEntryIdsJson());
                List<VocabularyCatalogEntry> entries = entryMapper.selectBatchIds(entryIds);
                Map<Long, VocabularyCatalogEntry> entryMap = entries.stream()
                        .collect(Collectors.toMap(VocabularyCatalogEntry::getId, item -> item));
                if (entryMap.size() != entryIds.size()) {
                    throw LearningAssistantException.badRequest(LearningConstants.ErrorCode.JSON_PARSE_FAILED);
                }

                Set<Long> alreadyAnalyzedIds = entryAnalysisMapper.selectList(
                        new LambdaQueryWrapper<VocabularyCatalogEntryAnalysis>()
                                .eq(VocabularyCatalogEntryAnalysis::getJobId, jobId)
                                .in(VocabularyCatalogEntryAnalysis::getCatalogEntryId, entryIds)
                                .eq(VocabularyCatalogEntryAnalysis::getDeleted, false))
                        .stream()
                        .map(VocabularyCatalogEntryAnalysis::getCatalogEntryId)
                        .collect(Collectors.toSet());

                List<VocabularyCatalogEntry> pendingEntries = entries.stream()
                        .filter(e -> !alreadyAnalyzedIds.contains(e.getId()))
                        .toList();

                if (pendingEntries.isEmpty()) {
                    batch.setStatus(LearningConstants.VocabularyAnalysis.ITEM_COMPLETED);
                    batch.setErrorMessage(null);
                    batch.setFinishedTime(LocalDateTime.now());
                    batch.setUpdateTime(LocalDateTime.now());
                    batchMapper.updateById(batch);
                } else {
                    int chunkSize = LearningConstants.VocabularyAnalysis.DEFAULT_BATCH_SIZE;
                    for (int i = 0; i < pendingEntries.size(); i += chunkSize) {
                        List<VocabularyCatalogEntry> chunk = pendingEntries.subList(
                                i, Math.min(pendingEntries.size(), i + chunkSize));
                        if (isAnalysisCancelled(job)) {
                            markAnalysisCancelled(job, batch,
                                    successCount + batchSuccessCount, failedCount + batchFailedCount);
                            return;
                        }
                        try {
                            AgentChatResponse response = requestBatch(job, chunk, modelConfigId);
                            if (isAnalysisCancelled(job)) {
                                markAnalysisCancelled(job, batch,
                                        successCount + batchSuccessCount, failedCount + batchFailedCount);
                                return;
                            }
                            AnalysisParseResult parsed = parseAnalyses(job, batch, chunk, response);
                            if (!parsed.analyses().isEmpty()) {
                                transactionTemplate.executeWithoutResult(status -> saveBatchResult(
                                        batch, parsed.analyses(), userId));
                                batchSuccessCount += parsed.analyses().size();
                            }
                            unresolvedEntryIds.addAll(parsed.unresolvedEntryIds());
                            batchFailedCount += parsed.unresolvedEntryIds().size();
                        } catch (RuntimeException ex) {
                            unresolvedEntryIds.addAll(chunk.stream()
                                    .map(VocabularyCatalogEntry::getId).toList());
                            batchFailedCount += chunk.size();
                            log.debug("公共词本关联分析响应处理失败 jobId={} batchNo={} chunkSize={}",
                                    jobId, batch.getBatchNo(), chunk.size(), ex);
                        }
                    }
                    boolean batchFullyCompleted = unresolvedEntryIds.isEmpty()
                            && (alreadyAnalyzedIds.size() + batchSuccessCount) >= entries.size();
                    batch.setStatus(batchFullyCompleted
                            ? LearningConstants.VocabularyAnalysis.ITEM_COMPLETED
                            : LearningConstants.VocabularyAnalysis.ITEM_FAILED);
                    batch.setErrorMessage(batchFullyCompleted
                            ? null : partialBatchError(entries, unresolvedEntryIds));
                    batch.setFinishedTime(LocalDateTime.now());
                    batch.setUpdateTime(LocalDateTime.now());
                    batchMapper.updateById(batch);
                }

                int jobSuccessCount = entryAnalysisMapper.selectCount(
                        new LambdaQueryWrapper<VocabularyCatalogEntryAnalysis>()
                                .eq(VocabularyCatalogEntryAnalysis::getJobId, jobId)
                                .eq(VocabularyCatalogEntryAnalysis::getDeleted, false)).intValue();
                successCount = jobSuccessCount;
                failedCount = Math.max(0, value(job.getTotalCount()) - successCount);
            } catch (RuntimeException ex) {
                int jobSuccessCount = entryAnalysisMapper.selectCount(
                        new LambdaQueryWrapper<VocabularyCatalogEntryAnalysis>()
                                .eq(VocabularyCatalogEntryAnalysis::getJobId, jobId)
                                .eq(VocabularyCatalogEntryAnalysis::getDeleted, false)).intValue();
                successCount = jobSuccessCount;
                failedCount = Math.max(0, value(job.getTotalCount()) - successCount);
                batch.setStatus(LearningConstants.VocabularyAnalysis.ITEM_FAILED);
                batch.setErrorMessage(limitError(ex.getMessage()));
                batch.setFinishedTime(LocalDateTime.now());
                batch.setUpdateTime(LocalDateTime.now());
                batchMapper.updateById(batch);
                log.info("公共词本关联分析批次失败 jobId={} batchNo={}", jobId, batch.getBatchNo());
                log.debug("公共词本关联分析批次技术异常 jobId={} batchNo={}", jobId, batch.getBatchNo(), ex);
            }
            job.setSuccessCount(successCount);
            job.setFailedCount(failedCount);
            job.setUpdateTime(LocalDateTime.now());
            jobMapper.updateById(job);
            if (job.getAsyncTaskId() != null) {
                asyncTaskService.updateProgress(job.getAsyncTaskId(), value(job.getTotalCount()),
                        successCount, failedCount);
            }
        }

        if (isAnalysisCancelled(job)) {
            markAnalysisCancelled(job, null, successCount, failedCount);
            return;
        }

        int remaining = batchMapper.selectCount(new LambdaQueryWrapper<VocabularyCatalogAnalysisBatch>()
                .eq(VocabularyCatalogAnalysisBatch::getJobId, jobId)
                .in(VocabularyCatalogAnalysisBatch::getStatus, List.of(
                        LearningConstants.VocabularyAnalysis.ITEM_PENDING,
                        LearningConstants.VocabularyAnalysis.ITEM_RUNNING,
                        LearningConstants.VocabularyAnalysis.ITEM_FAILED))
                .eq(VocabularyCatalogAnalysisBatch::getDeleted, false)).intValue();
        String finalStatus = failedCount > 0
                ? (successCount > 0 ? LearningConstants.VocabularyAnalysis.STATUS_PARTIAL_FAILED
                : LearningConstants.VocabularyAnalysis.STATUS_FAILED)
                : LearningConstants.VocabularyAnalysis.STATUS_COMPLETED;
        if (remaining > 0 && failedCount == 0) {
            finalStatus = LearningConstants.VocabularyAnalysis.STATUS_PARTIAL_FAILED;
        }
        job.setStatus(finalStatus);
        job.setGroupCount(distinctGroupCount(jobId));
        job.setFinishedTime(LocalDateTime.now());
        job.setUpdateTime(LocalDateTime.now());
        jobMapper.updateById(job);
        if (job.getAsyncTaskId() != null) {
            asyncTaskService.updateProgress(job.getAsyncTaskId(), value(job.getTotalCount()), successCount, failedCount);
            asyncTaskService.complete(job.getAsyncTaskId(), finalStatus, latestBatchError(jobId));
        }
    }

    /** 返回各次成功任务中每个词条最新的分析结果，供学习计划候选统筹复用。 */
    public List<VocabularyCatalogEntryAnalysis> readyEntries(Long catalogVersionId) {
        List<VocabularyCatalogAnalysisJob> jobs = jobMapper.selectList(
                new LambdaQueryWrapper<VocabularyCatalogAnalysisJob>()
                        .eq(VocabularyCatalogAnalysisJob::getCatalogVersionId, catalogVersionId)
                        .in(VocabularyCatalogAnalysisJob::getStatus, List.of(
                                LearningConstants.VocabularyAnalysis.STATUS_COMPLETED,
                                LearningConstants.VocabularyAnalysis.STATUS_PARTIAL_FAILED))
                        .eq(VocabularyCatalogAnalysisJob::getDeleted, false)
                        .orderByDesc(VocabularyCatalogAnalysisJob::getAnalysisVersion));
        if (jobs.isEmpty()) {
            return List.of();
        }
        Map<Long, Integer> jobVersion = jobs.stream().collect(Collectors.toMap(
                VocabularyCatalogAnalysisJob::getId, VocabularyCatalogAnalysisJob::getAnalysisVersion));
        return entryAnalysisMapper.selectList(new LambdaQueryWrapper<VocabularyCatalogEntryAnalysis>()
                        .in(VocabularyCatalogEntryAnalysis::getJobId, jobVersion.keySet())
                        .eq(VocabularyCatalogEntryAnalysis::getDeleted, false)
                        .in(VocabularyCatalogEntryAnalysis::getStatus, List.of(
                                LearningConstants.VocabularyAnalysis.ENTRY_READY,
                                LearningConstants.VocabularyAnalysis.ENTRY_LOW_CONFIDENCE)))
                .stream()
                .sorted((left, right) -> Integer.compare(
                        jobVersion.getOrDefault(right.getJobId(), 0),
                        jobVersion.getOrDefault(left.getJobId(), 0)))
                .collect(Collectors.toMap(VocabularyCatalogEntryAnalysis::getCatalogEntryId,
                        item -> item, (latest, older) -> latest, LinkedHashMap::new))
                .values().stream().toList();
    }

    private VocabularyCatalogAnalysisJob createJob(Long userId, VocabularyCatalog catalog,
                                                   VocabularyCatalogVersion version,
                                                   List<VocabularyCatalogEntry> entries, int batchSize,
                                                   VocabularyCatalogAnalysisRequest request) {
        LocalDateTime now = LocalDateTime.now();
        VocabularyCatalogAnalysisJob job = new VocabularyCatalogAnalysisJob();
        job.setUserId(userId);
        job.setCatalogId(catalog.getId());
        job.setCatalogVersionId(version.getId());
        job.setAnalysisVersion(nextAnalysisVersion(version.getId()));
        job.setStatus(LearningConstants.VocabularyAnalysis.STATUS_PENDING);
        job.setBatchSize(batchSize);
        job.setTotalCount(entries.size());
        job.setSuccessCount(LearningConstants.ZERO);
        job.setFailedCount(LearningConstants.ZERO);
        job.setGroupCount(LearningConstants.ZERO);
        job.setCreateBy(userId);
        job.setUpdateBy(userId);
        job.setCreateTime(now);
        job.setUpdateTime(now);
        job.setDeleted(false);
        job.setVersion(LearningConstants.ZERO);
        jobMapper.insert(job);

        List<VocabularyCatalogAnalysisBatch> batches = new ArrayList<>();
        int batchNo = LearningConstants.FIRST_SEQUENCE;
        for (int offset = 0; offset < entries.size(); offset += batchSize) {
            List<VocabularyCatalogEntry> batchEntries = entries.subList(offset,
                    Math.min(entries.size(), offset + batchSize));
            VocabularyCatalogAnalysisBatch batch = new VocabularyCatalogAnalysisBatch();
            batch.setId(com.baomidou.mybatisplus.core.toolkit.IdWorker.getId());
            batch.setJobId(job.getId());
            batch.setBatchNo(batchNo++);
            batch.setEntryCount(batchEntries.size());
            batch.setEntryIdsJson(writeJson(batchEntries.stream().map(VocabularyCatalogEntry::getId).toList()));
            batch.setStatus(LearningConstants.VocabularyAnalysis.ITEM_PENDING);
            batch.setAttemptCount(LearningConstants.ZERO);
            batch.setCreateBy(userId);
            batch.setUpdateBy(userId);
            batch.setCreateTime(now);
            batch.setUpdateTime(now);
            batch.setDeleted(false);
            batch.setVersion(LearningConstants.ZERO);
            batches.add(batch);
        }
        batchMapper.insertBatch(batches);

        Map<String, Object> payload = new HashMap<>();
        payload.put("analysisJobId", job.getId());
        payload.put("modelConfigId", request.getModelConfigId() == null ? "" : request.getModelConfigId());
        AiAsyncTask task = asyncTaskService.create(userId,
                LearningConstants.AiTask.TYPE_VOCABULARY_CATALOG_ANALYSIS,
                "公共词本关联分析",
                null, null, job.getId(), request.getExecutionMode(), null,
                LearningConstants.AiTask.DEFAULT_PRIORITY, entries.size(), payload);
        job.setAsyncTaskId(task.getId());
        job.setUpdateTime(LocalDateTime.now());
        jobMapper.updateById(job);
        return job;
    }

    private AgentChatResponse requestBatch(VocabularyCatalogAnalysisJob job,
                                            List<VocabularyCatalogEntry> entries, Long modelConfigId) {
        List<Map<String, Object>> words = entries.stream().map(entry -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("entry_id", entry.getId());
            item.put("term", entry.effectiveTerm());
            item.put("phonetic", entry.getPhonetic());
            item.put("meaning", entry.getDefinitionText());
            return item;
        }).toList();
        Map<String, Object> variables = new HashMap<>();
        variables.put("catalog_name", job.getCatalogId());
        variables.put("analysis_version", job.getAnalysisVersion());
        variables.put("words", words);

        AgentChatRequest request = new AgentChatRequest();
        request.setUserId(job.getUserId());
        request.setInvocationScene(AiInvocationScene.VOCABULARY_CATALOG_ANALYSIS);
        request.setAgentCode(LearningConstants.VOCABULARY_ANALYSIS_AGENT_CODE);
        request.setTemplateCode(LearningConstants.VOCABULARY_ANALYSIS_TEMPLATE_CODE);
        request.setTitle("公共词本关联分析");
        request.setBusinessType("vocabulary_catalog_analysis");
        request.setBusinessId(String.valueOf(job.getCatalogVersionId()));
        request.setSceneCode("vocabulary_catalog_analysis");
        request.setModelConfigId(modelConfigId);
        request.setMessage("请分析这批公共词本词条，输出可复用的语义索引。");
        request.setVariables(variables);
        return aiChatService.chat(request);
    }

    AnalysisParseResult parseAnalyses(VocabularyCatalogAnalysisJob job,
                                      VocabularyCatalogAnalysisBatch batch,
                                      List<VocabularyCatalogEntry> entries,
                                      AgentChatResponse response) {
        JsonNode root = parseJson(response.getContent());
        JsonNode array = root.path("entries");
        if (!array.isArray()) {
            throw LearningAssistantException.badRequest(LearningConstants.ErrorCode.AI_RESPONSE_PARSE_FAILED);
        }
        Map<Long, VocabularyCatalogEntry> sourceById = entries.stream()
                .collect(Collectors.toMap(VocabularyCatalogEntry::getId, item -> item));
        Map<String, VocabularyCatalogEntry> sourceByTerm = entries.stream()
                .filter(e -> StringUtils.hasText(e.getNormalizedTerm()))
                .collect(Collectors.toMap(e -> e.getNormalizedTerm().toLowerCase().trim(), e -> e, (a, b) -> a));

        Map<Long, JsonNode> resultById = new LinkedHashMap<>();
        int arrayIndex = 0;
        for (JsonNode item : array) {
            Long entryId = longValue(item, "entry_id", "entryId", "id");
            if (entryId != null && sourceById.containsKey(entryId) && !resultById.containsKey(entryId)) {
                resultById.put(entryId, item);
            } else {
                String term = text(item, "term", "word", "normalized_term");
                if (term != null && sourceByTerm.containsKey(term.toLowerCase().trim())) {
                    Long matchedId = sourceByTerm.get(term.toLowerCase().trim()).getId();
                    if (!resultById.containsKey(matchedId)) {
                        resultById.put(matchedId, item);
                    }
                } else if (arrayIndex < entries.size()) {
                    Long positionalId = entries.get(arrayIndex).getId();
                    if (!resultById.containsKey(positionalId)) {
                        resultById.put(positionalId, item);
                    }
                }
            }
            arrayIndex++;
        }

        LocalDateTime now = LocalDateTime.now();
        List<VocabularyCatalogEntryAnalysis> analyses = new ArrayList<>();
        Set<Long> resolvedEntryIds = new LinkedHashSet<>();
        for (VocabularyCatalogEntry entry : entries) {
            JsonNode item = resultById.get(entry.getId());
            if (item == null) {
                continue;
            }
            try {
                validateAnalysisItem(item);
            } catch (LearningAssistantException ex) {
                log.debug("公共词本关联分析词条字段校验失败 jobId={} batchNo={} entryId={}",
                        job.getId(), batch.getBatchNo(), entry.getId(), ex);
                continue;
            }
            VocabularyCatalogEntryAnalysis analysis = new VocabularyCatalogEntryAnalysis();
            analysis.setId(com.baomidou.mybatisplus.core.toolkit.IdWorker.getId());
            analysis.setJobId(job.getId());
            analysis.setCatalogId(job.getCatalogId());
            analysis.setCatalogVersionId(job.getCatalogVersionId());
            analysis.setCatalogEntryId(entry.getId());
            analysis.setPrimaryGroupCode(text(item, "primary_group_code", "primaryGroupCode", "group_code"));
            analysis.setPrimaryGroupName(text(item, "primary_group_name", "primaryGroupName", "group_name"));
            analysis.setDomainCode(text(item, "domain", "domain_code", "domainCode"));
            analysis.setSubTopicCode(text(item, "sub_topic", "sub_topic_code", "subTopicCode"));
            analysis.setTagsJson(writeJson(textList(item.path("tags"), LearningConstants.VocabularyAnalysis.MAX_TAG_COUNT)));
            analysis.setRelatedEntryIdsJson(writeJson(longList(item.path("related_entry_ids"),
                    LearningConstants.VocabularyAnalysis.MAX_RELATED_COUNT, sourceById.keySet())));
            analysis.setDifficultyLevel(text(item, "difficulty_level", "difficulty"));
            double confidence = number(item, "confidence");
            analysis.setConfidence(confidence);
            analysis.setStatus(confidence < LearningConstants.VocabularyAnalysis.LOW_CONFIDENCE_THRESHOLD
                    ? LearningConstants.VocabularyAnalysis.ENTRY_LOW_CONFIDENCE
                    : LearningConstants.VocabularyAnalysis.ENTRY_READY);
            analysis.setSource(LearningConstants.VocabularyAnalysis.SOURCE_AI);
            analysis.setAnalysisVersion(job.getAnalysisVersion());
            analysis.setRawResultJson(writeJson(item));
            analysis.setCreateBy(job.getUserId());
            analysis.setUpdateBy(job.getUserId());
            analysis.setCreateTime(now);
            analysis.setUpdateTime(now);
            analysis.setDeleted(false);
            analysis.setVersion(LearningConstants.ZERO);
            analyses.add(analysis);
            resolvedEntryIds.add(entry.getId());
        }
        List<Long> unresolvedEntryIds = sourceById.keySet().stream()
                .filter(entryId -> !resolvedEntryIds.contains(entryId)).toList();
        if (!unresolvedEntryIds.isEmpty()) {
            List<String> unresolvedTerms = entries.stream()
                    .filter(entry -> unresolvedEntryIds.contains(entry.getId()))
                    .map(VocabularyCatalogEntry::effectiveTerm)
                    .toList();
            log.warn("公共词本关联分析响应未完全覆盖当前批次词条: jobId={} batchNo={} expectedCount={} resolvedCount={} unresolvedCount={} 未覆盖词汇={}",
                    job.getId(), batch.getBatchNo(), entries.size(), resolvedEntryIds.size(), unresolvedEntryIds.size(), unresolvedTerms);
        }
        return new AnalysisParseResult(List.copyOf(analyses), unresolvedEntryIds);
    }

    private void validateAnalysisItem(JsonNode item) {
        if (!item.isObject()
                || !StringUtils.hasText(text(item, "primary_group_code", "primaryGroupCode", "group_code"))
                || !StringUtils.hasText(text(item, "primary_group_name", "primaryGroupName", "group_name"))
                || !StringUtils.hasText(text(item, "domain", "domain_code", "domainCode"))
                || !StringUtils.hasText(text(item, "sub_topic", "sub_topic_code", "subTopicCode"))
                || !StringUtils.hasText(text(item, "difficulty_level", "difficulty"))
                || !item.path("tags").isArray()
                || !item.path("related_entry_ids").isArray()) {
            throw LearningAssistantException.badRequest(LearningConstants.ErrorCode.AI_RESPONSE_PARSE_FAILED);
        }
        JsonNode confidence = item.path("confidence");
        if (!confidence.isNumber()
                || !Double.isFinite(confidence.doubleValue())
                || confidence.doubleValue() < 0.0D
                || confidence.doubleValue() > 1.0D) {
            throw LearningAssistantException.badRequest(LearningConstants.ErrorCode.AI_RESPONSE_PARSE_FAILED);
        }
    }

    private String partialBatchError(List<VocabularyCatalogEntry> entries, List<Long> unresolvedEntryIds) {
        List<String> terms = (entries == null ? List.<VocabularyCatalogEntry>of() : entries).stream()
                .filter(entry -> unresolvedEntryIds.contains(entry.getId()))
                .map(VocabularyCatalogEntry::effectiveTerm)
                .limit(5)
                .toList();
        String suffix = unresolvedEntryIds.size() > 5 ? " 等" : "";
        String wordsHint = terms.isEmpty() ? "" : "（" + String.join("、", terms) + suffix + "）";
        return limitError("AI 本次未覆盖 " + unresolvedEntryIds.size() + " 个词条" + wordsHint + "，将在下次分析任务中重试");
    }

    private void saveBatchResult(VocabularyCatalogAnalysisBatch batch,
                                 List<VocabularyCatalogEntryAnalysis> analyses, Long userId) {
        List<Long> ids = analyses.stream().map(VocabularyCatalogEntryAnalysis::getCatalogEntryId).toList();
        if (!ids.isEmpty()) {
            entryAnalysisMapper.delete(new LambdaQueryWrapper<VocabularyCatalogEntryAnalysis>()
                    .eq(VocabularyCatalogEntryAnalysis::getJobId, batch.getJobId())
                    .in(VocabularyCatalogEntryAnalysis::getCatalogEntryId, ids));
            entryAnalysisMapper.insertBatch(analyses);
        }
    }

    private void markBatchRunning(VocabularyCatalogAnalysisBatch batch, Long userId) {
        batch.setStatus(LearningConstants.VocabularyAnalysis.ITEM_RUNNING);
        batch.setAttemptCount(value(batch.getAttemptCount()) + LearningConstants.SEQUENCE_STEP);
        batch.setStartedTime(LocalDateTime.now());
        batch.setFinishedTime(null);
        batch.setUpdateBy(userId);
        batch.setUpdateTime(LocalDateTime.now());
        batchMapper.updateById(batch);
    }

    private boolean isAnalysisCancelled(VocabularyCatalogAnalysisJob job) {
        return job.getAsyncTaskId() != null && asyncTaskService.isCancelled(job.getAsyncTaskId());
    }

    private void markAnalysisCancelled(VocabularyCatalogAnalysisJob job,
                                       VocabularyCatalogAnalysisBatch activeBatch,
                                       int successCount, int failedCount) {
        LocalDateTime now = LocalDateTime.now();
        if (activeBatch != null
                && LearningConstants.VocabularyAnalysis.ITEM_RUNNING.equals(activeBatch.getStatus())) {
            activeBatch.setStatus(LearningConstants.VocabularyAnalysis.ITEM_FAILED);
            activeBatch.setErrorMessage("任务已取消，未完成词条将在下次分析任务中重试");
            activeBatch.setFinishedTime(now);
            activeBatch.setUpdateTime(now);
            batchMapper.updateById(activeBatch);
        }
        job.setStatus(LearningConstants.VocabularyAnalysis.STATUS_CANCELLED);
        job.setSuccessCount(Math.max(LearningConstants.ZERO, successCount));
        job.setFailedCount(Math.max(LearningConstants.ZERO, failedCount));
        job.setErrorMessage("用户已取消分析任务");
        job.setFinishedTime(now);
        job.setUpdateTime(now);
        jobMapper.updateById(job);
    }

    private VocabularyCatalogAnalysisJob latestJob(Long catalogVersionId) {
        return jobMapper.selectOne(new LambdaQueryWrapper<VocabularyCatalogAnalysisJob>()
                .eq(VocabularyCatalogAnalysisJob::getCatalogVersionId, catalogVersionId)
                .eq(VocabularyCatalogAnalysisJob::getDeleted, false)
                .orderByDesc(VocabularyCatalogAnalysisJob::getAnalysisVersion)
                .last(LearningConstants.SQL_LIMIT_ONE));
    }

    private VocabularyCatalogAnalysisJob latestCompletedJob(Long catalogVersionId) {
        return jobMapper.selectOne(new LambdaQueryWrapper<VocabularyCatalogAnalysisJob>()
                .eq(VocabularyCatalogAnalysisJob::getCatalogVersionId, catalogVersionId)
                .in(VocabularyCatalogAnalysisJob::getStatus, List.of(
                        LearningConstants.VocabularyAnalysis.STATUS_COMPLETED,
                        LearningConstants.VocabularyAnalysis.STATUS_PARTIAL_FAILED))
                .eq(VocabularyCatalogAnalysisJob::getDeleted, false)
                .orderByDesc(VocabularyCatalogAnalysisJob::getAnalysisVersion)
                .last(LearningConstants.SQL_LIMIT_ONE));
    }

    /** 只有词本版本所有已发布词条均有有效分析结果时，才认为无需再次触发。 */
    private boolean hasUnanalyzedEntries(Long catalogVersionId) {
        return entryMapper.countUnanalyzedPublished(catalogVersionId) > 0;
    }

    private int nextAnalysisVersion(Long catalogVersionId) {
        VocabularyCatalogAnalysisJob latest = latestJob(catalogVersionId);
        return latest == null ? LearningConstants.FIRST_SEQUENCE : value(latest.getAnalysisVersion()) + 1;
    }

    private int distinctGroupCount(Long jobId) {
        return entryAnalysisMapper.selectList(new LambdaQueryWrapper<VocabularyCatalogEntryAnalysis>()
                        .eq(VocabularyCatalogEntryAnalysis::getJobId, jobId)
                        .eq(VocabularyCatalogEntryAnalysis::getDeleted, false))
                .stream().map(VocabularyCatalogEntryAnalysis::getPrimaryGroupCode)
                .filter(StringUtils::hasText).collect(Collectors.toSet()).size();
    }

    private String latestBatchError(Long jobId) {
        return batchMapper.selectList(new LambdaQueryWrapper<VocabularyCatalogAnalysisBatch>()
                        .eq(VocabularyCatalogAnalysisBatch::getJobId, jobId)
                        .eq(VocabularyCatalogAnalysisBatch::getStatus, LearningConstants.VocabularyAnalysis.ITEM_FAILED)
                        .eq(VocabularyCatalogAnalysisBatch::getDeleted, false)
                        .orderByDesc(VocabularyCatalogAnalysisBatch::getBatchNo))
                .stream().map(VocabularyCatalogAnalysisBatch::getErrorMessage)
                .filter(StringUtils::hasText).findFirst().orElse(null);
    }

    private VocabularyCatalogVersion requirePublishedVersion(Long versionId) {
        VocabularyCatalogVersion version = versionMapper.selectOne(new LambdaQueryWrapper<VocabularyCatalogVersion>()
                .eq(VocabularyCatalogVersion::getId, versionId)
                .eq(VocabularyCatalogVersion::getStatus, LearningConstants.VocabularyImport.VERSION_STATUS_PUBLISHED)
                .eq(VocabularyCatalogVersion::getDeleted, false)
                .last(LearningConstants.SQL_LIMIT_ONE));
        if (version == null) {
            throw LearningAssistantException.notFound(LearningConstants.ErrorCode.VOCABULARY_CATALOG_NOT_FOUND);
        }
        return version;
    }

    private void requireCatalogReadable(Long userId, Long catalogId) {
        VocabularyCatalog catalog = catalogMapper.selectOne(new LambdaQueryWrapper<VocabularyCatalog>()
                .eq(VocabularyCatalog::getId, catalogId)
                .and(wrapper -> wrapper.eq(VocabularyCatalog::getOwnerUserId, userId)
                        .or().eq(VocabularyCatalog::getVisibility, LearningConstants.VocabularyImport.VISIBILITY_PUBLIC))
                .eq(VocabularyCatalog::getStatus, LearningConstants.VocabularyImport.CATALOG_STATUS_PUBLISHED)
                .eq(VocabularyCatalog::getDeleted, false)
                .last(LearningConstants.SQL_LIMIT_ONE));
        if (catalog == null) {
            throw LearningAssistantException.notFound(LearningConstants.ErrorCode.VOCABULARY_CATALOG_NOT_FOUND);
        }
    }

    private VocabularyCatalogAnalysisResponse toResponse(VocabularyCatalogAnalysisJob job) {
        VocabularyCatalogAnalysisResponse response = new VocabularyCatalogAnalysisResponse();
        response.setJobId(job.getId());
        response.setCatalogId(job.getCatalogId());
        response.setCatalogVersionId(job.getCatalogVersionId());
        response.setAsyncTaskId(job.getAsyncTaskId());
        response.setAnalysisVersion(job.getAnalysisVersion());
        response.setStatus(job.getStatus());
        response.setBatchSize(job.getBatchSize());
        response.setTotalCount(job.getTotalCount());
        response.setSuccessCount(job.getSuccessCount());
        response.setFailedCount(job.getFailedCount());
        response.setGroupCount(job.getGroupCount());
        fillCoverage(response, job.getCatalogVersionId());
        response.setErrorMessage(job.getErrorMessage());
        response.setStartedTime(job.getStartedTime());
        response.setFinishedTime(job.getFinishedTime());
        response.setCreateTime(job.getCreateTime());
        response.setUpdateTime(job.getUpdateTime());
        return response;
    }

    private void fillCoverage(VocabularyCatalogAnalysisResponse response, Long catalogVersionId) {
        int publishedCount = entryMapper.selectCount(new LambdaQueryWrapper<VocabularyCatalogEntry>()
                .eq(VocabularyCatalogEntry::getCatalogVersionId, catalogVersionId)
                .eq(VocabularyCatalogEntry::getPublished, true)
                .eq(VocabularyCatalogEntry::getDeleted, false)).intValue();
        int unanalyzedCount = entryMapper.countUnanalyzedPublished(catalogVersionId);
        int analyzedCount = Math.max(0, publishedCount - unanalyzedCount);
        response.setPublishedCount(publishedCount);
        response.setAnalyzedCount(analyzedCount);
        response.setUnanalyzedCount(unanalyzedCount);
        response.setCanTrigger(response.getUnanalyzedCount() > 0
                && !List.of(LearningConstants.VocabularyAnalysis.STATUS_PENDING,
                LearningConstants.VocabularyAnalysis.STATUS_RUNNING).contains(response.getStatus()));
    }

    private static final Pattern JSON_BLOCK_PATTERN = Pattern.compile("\\{[\\s\\S]*\\}");

    private JsonNode parseJson(String content) {
        if (!org.springframework.util.StringUtils.hasText(content)) {
            throw LearningAssistantException.badRequest(LearningConstants.ErrorCode.AI_RESPONSE_PARSE_FAILED);
        }
        String cleaned = content.replace("```json", "").replace("```", "").trim();
        try {
            return objectMapper.readTree(cleaned);
        } catch (Exception ignored) {
            Matcher matcher = JSON_BLOCK_PATTERN.matcher(cleaned);
            if (matcher.find()) {
                try {
                    return objectMapper.readTree(matcher.group());
                } catch (Exception ex) {
                    log.debug("词本分析响应 JSON 提取解析失败 length={}", content.length(), ex);
                }
            }
            log.debug("公共词本关联分析响应不是合法 JSON length={}", content.length());
            throw LearningAssistantException.badRequest(LearningConstants.ErrorCode.AI_RESPONSE_PARSE_FAILED);
        }
    }

    private List<Long> readIds(String json) {
        try {
            return objectMapper.readValue(json == null ? "[]" : json, new TypeReference<>() {
            });
        } catch (JsonProcessingException ex) {
            throw LearningAssistantException.badRequest(LearningConstants.ErrorCode.JSON_PARSE_FAILED);
        }
    }

    private List<String> textList(JsonNode node, int limit) {
        if (node == null || !node.isArray()) return List.of();
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            if (StringUtils.hasText(item.asText())) {
                values.add(item.asText().trim());
            }
            if (values.size() >= limit) break;
        }
        return List.copyOf(values);
    }

    private List<Long> longList(JsonNode node, int limit, Set<Long> allowed) {
        if (node == null || !node.isArray()) return List.of();
        List<Long> values = new ArrayList<>();
        for (JsonNode item : node) {
            Long value = item.isIntegralNumber() ? item.longValue() : parseLong(item.asText());
            if (value != null && allowed.contains(value)) values.add(value);
            if (values.size() >= limit) break;
        }
        return List.copyOf(values);
    }

    private String text(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.path(name);
            if (!value.isMissingNode() && StringUtils.hasText(value.asText())) return value.asText().trim();
        }
        return null;
    }

    private Long longValue(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.path(name);
            if (value.isIntegralNumber()) return value.longValue();
            if (StringUtils.hasText(value.asText())) return parseLong(value.asText());
        }
        return null;
    }

    private double number(JsonNode node, String name) {
        double value = node.path(name).asDouble(0.0D);
        return Math.max(0.0D, Math.min(1.0D, value));
    }

    private Long parseLong(String value) {
        try {
            return Long.valueOf(value);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw LearningAssistantException.badRequest(LearningConstants.ErrorCode.JSON_SERIALIZE_FAILED);
        }
    }

    private int resolveBatchSize(Integer value) {
        int requested = value == null ? LearningConstants.VocabularyAnalysis.DEFAULT_BATCH_SIZE : value;
        return Math.max(LearningConstants.VocabularyAnalysis.MIN_BATCH_SIZE,
                Math.min(requested, LearningConstants.VocabularyAnalysis.MAX_BATCH_SIZE));
    }

    private int value(Integer value) {
        return value == null ? 0 : value;
    }

    private String limitError(String value) {
        if (value == null) return null;
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }
}
