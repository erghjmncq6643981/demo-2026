package com.chandler.learning.agent.vocabulary.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chandler.learning.agent.ai.chat.application.AgentChatRequest;
import com.chandler.learning.agent.ai.chat.application.AgentChatResponse;
import com.chandler.learning.agent.vocabulary.api.response.VocabularyCardGenerationItemResponse;
import com.chandler.learning.agent.vocabulary.api.request.VocabularyCardGenerationRequest;
import com.chandler.learning.agent.vocabulary.api.response.VocabularyCardGenerationResponse;
import com.chandler.learning.agent.task.domain.entity.AiAsyncTask;
import com.chandler.learning.agent.learning.domain.entity.LearningPlan;
import com.chandler.learning.agent.learning.domain.entity.LearningPlanUnitEntry;
import com.chandler.learning.agent.vocabulary.domain.entity.LearningWordProgress;
import com.chandler.learning.agent.vocabulary.domain.entity.EnglishVocabularyStudyRecord;
import com.chandler.learning.agent.vocabulary.domain.entity.VocabularyCardGenerationJob;
import com.chandler.learning.agent.vocabulary.domain.entity.VocabularyCardGenerationJobItem;
import com.chandler.learning.agent.vocabulary.domain.bo.VocabularyCardGenerationProgress;
import com.chandler.learning.agent.ai.chat.domain.enums.AiInvocationScene;
import com.chandler.learning.agent.learning.domain.enums.LearningScene;
import com.chandler.learning.agent.system.domain.enums.SystemLogType;
import com.chandler.learning.agent.exception.LearningAssistantException;
import com.chandler.learning.agent.learning.application.LearningPlanAccessService;
import com.chandler.learning.agent.vocabulary.infrastructure.mapper.LearningWordProgressMapper;
import com.chandler.learning.agent.vocabulary.infrastructure.mapper.EnglishVocabularyStudyRecordMapper;
import com.chandler.learning.agent.vocabulary.infrastructure.mapper.VocabularyCardGenerationJobItemMapper;
import com.chandler.learning.agent.vocabulary.infrastructure.mapper.VocabularyCardGenerationJobMapper;
import com.chandler.learning.agent.ai.chat.application.AiChatService;
import com.chandler.learning.agent.task.application.AiAsyncTaskService;
import com.chandler.learning.agent.system.application.SystemLogService;
import com.chandler.learning.agent.identity.application.UserDisplayNameService;
import com.chandler.learning.agent.vocabulary.application.VocabularyInsightService;
import com.chandler.learning.agent.vocabulary.application.WordbookService;
import com.chandler.learning.agent.ai.agent.domain.constant.AiScenarioConstants;
import com.chandler.learning.agent.ai.chat.domain.constant.AiChatConstants;
import com.chandler.learning.agent.common.constant.CommonConstants;
import com.chandler.learning.agent.common.exception.LearningErrorCode;
import com.chandler.learning.agent.task.domain.constant.AiTaskConstants;
import com.chandler.learning.agent.vocabulary.domain.constant.VocabularyCardConstants;
import com.chandler.learning.agent.vocabulary.domain.constant.VocabularyConstants;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 对当前场景的必要词卡先查共享缓存，再将缺失项按 10-20 个一批生成。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VocabularyCardBatchService {

    private final VocabularyCardGenerationJobMapper jobMapper;
    private final VocabularyCardGenerationJobItemMapper itemMapper;
    private final EnglishVocabularyStudyRecordMapper vocabularyMapper;
    private final LearningPlanAccessService learningPlanAccessService;
    private final LearningWordProgressMapper progressMapper;
    private final AiChatService aiChatService;
    private final WordbookService wordbookService;
    private final VocabularyInsightService insightService;
    private final SystemLogService systemLogService;
    private final UserDisplayNameService userDisplayNameService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final ApplicationEventPublisher eventPublisher;
    private final AiAsyncTaskService aiAsyncTaskService;

    /**
     * 为一个场景中的核心和复习词创建批任务。
     */
    public VocabularyCardGenerationResponse generate(Long userId, Long planId, Long unitId,
                                                     VocabularyCardGenerationRequest request) {
        LearningPlan plan = learningPlanAccessService.requireOwnedPlan(userId, planId);
        learningPlanAccessService.requireUnit(plan, unitId);
        int batchSize = resolveBatchSize(request == null ? null : request.getBatchSize());
        List<LearningPlanUnitEntry> unitEntries = learningPlanAccessService.listVocabularyCardEntries(unitId);
        Map<String, LearningPlanUnitEntry> unique = unitEntries.stream().collect(Collectors.toMap(
                LearningPlanUnitEntry::getNormalizedTerm, entry -> entry,
                (left, right) -> left, LinkedHashMap::new));
        Long modelConfigId = request == null ? null : request.getModelConfigId();
        String executionMode = request == null ? null : request.getExecutionMode();
        LocalDateTime scheduledTime = request == null ? null : request.getScheduledTime();
        Integer priority = request == null ? null : request.getPriority();
        GenerationSubmission submission = transactionTemplate.execute(status -> {
            // 同一场景单元串行创建任务，避免并发点击产生多个活动任务。
            learningPlanAccessService.lockUnit(planId, unitId);
            VocabularyCardGenerationJob activeJob = jobMapper.selectOne(
                    new LambdaQueryWrapper<VocabularyCardGenerationJob>()
                            .eq(VocabularyCardGenerationJob::getUserId, userId)
                            .eq(VocabularyCardGenerationJob::getUnitId, unitId)
                            .in(VocabularyCardGenerationJob::getStatus,
                                    List.of(VocabularyCardConstants.JOB_PENDING,
                                            VocabularyCardConstants.JOB_RUNNING))
                            .eq(VocabularyCardGenerationJob::getDeleted, false)
                            .orderByDesc(VocabularyCardGenerationJob::getCreateTime)
                            .last(CommonConstants.SQL_LIMIT_ONE));
            if (activeJob != null) {
                return new GenerationSubmission(activeJob, 0, true);
            }

            LocalDateTime now = LocalDateTime.now();
            VocabularyCardGenerationJob job = new VocabularyCardGenerationJob();
            job.setUserId(userId);
            job.setPlanId(planId);
            job.setUnitId(unitId);
            job.setStatus(VocabularyCardConstants.JOB_PENDING);
            job.setBatchSize(batchSize);
            job.setTotalCount(unique.size());
            job.setSuccessCount(CommonConstants.ZERO);
            job.setFailedCount(CommonConstants.ZERO);
            job.setDeleted(false);
            job.setCreateTime(now);
            job.setUpdateTime(now);
            List<VocabularyCardGenerationJobItem> items = new ArrayList<>();
            jobMapper.insert(job);
            for (LearningPlanUnitEntry entry : unique.values()) {
                VocabularyCardGenerationJobItem item = new VocabularyCardGenerationJobItem();
                item.setId(com.baomidou.mybatisplus.core.toolkit.IdWorker.getId());
                item.setJobId(job.getId());
                item.setWordProgressId(entry.getWordProgressId());
                item.setWordbookEntryId(entry.getWordbookEntryId());
                item.setTerm(entry.getTerm());
                item.setNormalizedTerm(entry.getNormalizedTerm());
                item.setStatus(VocabularyCardConstants.ITEM_PENDING);
                item.setAttemptCount(CommonConstants.ZERO);
                item.setCreateBy(userId);
                item.setUpdateBy(userId);
                item.setDeleted(false);
                item.setVersion(CommonConstants.ZERO);
                item.setCreateTime(now);
                item.setUpdateTime(now);
                items.add(item);
            }
            if (!items.isEmpty()) {
                itemMapper.insertBatch(items);
            }
            AiAsyncTask task = aiAsyncTaskService.create(userId,
                    AiTaskConstants.TYPE_VOCABULARY_CARD,
                    "批量生成场景词卡",
                    planId,
                    unitId,
                    job.getId(),
                    executionMode,
                    scheduledTime,
                    priority,
                    items.size(),
                    "vocabulary_card:" + planId + ":" + unitId + ":" + job.getId(),
                    Map.of("modelConfigId", modelConfigId == null ? "" : modelConfigId));
            job.setAsyncTaskId(task.getId());
            jobMapper.updateById(job);
            return new GenerationSubmission(job, items.size(), false);
        });
        if (submission.existing()) {
            return toResponse(submission.job());
        }
        systemLogService.record(userId, SystemLogType.AI, "批量生成场景词卡",
                plan.getName() + "，共 " + submission.itemCount() + " 个词");
        log.info("用户「{}」为计划「{}」的场景 {} 发起批量词卡任务，共 {} 个去重词，批大小 {}",
                userDisplayNameService.userName(userId), plan.getName(), unitId, submission.itemCount(), batchSize);
        return toResponse(jobMapper.selectById(submission.job().getId()));
    }

    /**
     * 仅重试一个任务中上次失败的单词。
     */
    public VocabularyCardGenerationResponse retryFailed(Long userId, Long jobId,
                                                        VocabularyCardGenerationRequest request) {
        VocabularyCardGenerationJob job = requireJob(userId, jobId);
        List<VocabularyCardGenerationJobItem> failed = itemMapper.selectList(
                new LambdaQueryWrapper<VocabularyCardGenerationJobItem>()
                        .eq(VocabularyCardGenerationJobItem::getJobId, jobId)
                        .eq(VocabularyCardGenerationJobItem::getStatus, VocabularyCardConstants.ITEM_FAILED)
                        .eq(VocabularyCardGenerationJobItem::getDeleted, false));
        if (failed.isEmpty()) {
            return toResponse(job);
        }
        Long modelConfigId = request == null ? null : request.getModelConfigId();
        Boolean requeued = transactionTemplate.execute(status -> {
            if (job.getAsyncTaskId() != null) {
                Map<String, Object> payload = modelConfigId == null
                        ? null : Map.of("modelConfigId", modelConfigId);
                AiAsyncTask task = aiAsyncTaskService.retry(userId, job.getAsyncTaskId(), payload);
                if (!AiTaskConstants.STATUS_PENDING.equals(task.getStatus())) {
                    return false;
                }
            }
            if (request != null && request.getBatchSize() != null) {
                job.setBatchSize(resolveBatchSize(request.getBatchSize()));
            }
            job.setStatus(VocabularyCardConstants.JOB_PENDING);
            job.setFinishedTime(null);
            job.setUpdateTime(LocalDateTime.now());
            jobMapper.updateById(job);
            if (job.getAsyncTaskId() == null) {
                eventPublisher.publishEvent(new VocabularyCardGenerationRequestedEvent(userId, jobId, modelConfigId));
            }
            return true;
        });
        if (!Boolean.TRUE.equals(requeued)) {
            return toResponse(jobMapper.selectById(jobId));
        }
        return toResponse(jobMapper.selectById(jobId));
    }

    /** 异步 Worker 使用任务 ID 重新读取明细，保证任务重启后仍可继续处理。 */
    public void executeJob(Long userId, Long jobId, Long modelConfigId) {
        requireJob(userId, jobId);
        int claimed = jobMapper.update(null, new LambdaUpdateWrapper<VocabularyCardGenerationJob>()
                .eq(VocabularyCardGenerationJob::getId, jobId)
                .in(VocabularyCardGenerationJob::getStatus,
                        List.of(VocabularyCardConstants.JOB_PENDING,
                                VocabularyCardConstants.JOB_RUNNING,
                                VocabularyCardConstants.JOB_FAILED,
                                VocabularyCardConstants.JOB_PARTIAL_FAILED,
                                VocabularyCardConstants.JOB_CANCELLED))
                .set(VocabularyCardGenerationJob::getStatus, VocabularyCardConstants.JOB_RUNNING)
                .set(VocabularyCardGenerationJob::getStartedTime, LocalDateTime.now())
                .set(VocabularyCardGenerationJob::getFinishedTime, null)
                .set(VocabularyCardGenerationJob::getUpdateTime, LocalDateTime.now()));
        if (claimed == CommonConstants.ZERO) {
            return;
        }
        VocabularyCardGenerationJob job = requireJob(userId, jobId);
        List<VocabularyCardGenerationJobItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<VocabularyCardGenerationJobItem>()
                        .eq(VocabularyCardGenerationJobItem::getJobId, jobId)
                        .in(VocabularyCardGenerationJobItem::getStatus,
                                List.of(VocabularyCardConstants.ITEM_PENDING,
                                        VocabularyCardConstants.ITEM_GENERATING,
                                        VocabularyCardConstants.ITEM_FAILED))
                        .eq(VocabularyCardGenerationJobItem::getDeleted, false)
                        .orderByAsc(VocabularyCardGenerationJobItem::getCreateTime));
        try {
            process(userId, job, items, modelConfigId);
            if (job.getAsyncTaskId() != null) {
                aiAsyncTaskService.updateProgress(job.getAsyncTaskId(),
                        value(job.getTotalCount()), value(job.getSuccessCount()), value(job.getFailedCount()));
                aiAsyncTaskService.complete(job.getAsyncTaskId(),
                        job.getStatus(), job.getErrorMessage());
            }
        } catch (RuntimeException ex) {
            job.setStatus(VocabularyCardConstants.JOB_FAILED);
            job.setErrorMessage(limitError(ex.getMessage()));
            job.setFinishedTime(LocalDateTime.now());
            job.setUpdateTime(LocalDateTime.now());
            jobMapper.updateById(job);
            if (job.getAsyncTaskId() != null) {
                aiAsyncTaskService.complete(job.getAsyncTaskId(), AiTaskConstants.STATUS_FAILED, ex.getMessage());
            }
            throw ex;
        }
    }

    public VocabularyCardGenerationResponse detail(Long userId, Long jobId) {
        return detail(userId, jobId, 1, VocabularyCardConstants.DEFAULT_ITEM_PAGE_SIZE);
    }

    /** 分页查询词卡任务明细，避免任务规模较大时返回超大响应。 */
    public VocabularyCardGenerationResponse detail(Long userId, Long jobId, Integer page, Integer pageSize) {
        VocabularyCardGenerationJob job = requireJob(userId, jobId);
        int resolvedPage = page == null || page < 1 ? 1 : page;
        int resolvedPageSize = pageSize == null
                ? VocabularyCardConstants.DEFAULT_ITEM_PAGE_SIZE
                : Math.max(1, Math.min(pageSize, VocabularyCardConstants.MAX_ITEM_PAGE_SIZE));
        return toResponse(job, resolvedPage, resolvedPageSize);
    }

    private void process(Long userId, VocabularyCardGenerationJob job,
                         List<VocabularyCardGenerationJobItem> items, Long modelConfigId) {
        LocalDateTime now = LocalDateTime.now();
        job.setStatus(VocabularyCardConstants.JOB_RUNNING);
        if (job.getStartedTime() == null) {
            job.setStartedTime(now);
        }
        job.setFinishedTime(null);
        job.setUpdateTime(now);
        jobMapper.updateById(job);
        if (items.isEmpty()) {
            finishJob(job);
            return;
        }

        Map<String, EnglishVocabularyStudyRecord> cached = findCached(items);
        List<VocabularyCardGenerationJobItem> misses = new ArrayList<>();
        Map<VocabularyCardGenerationJobItem, EnglishVocabularyStudyRecord> cacheHits = new LinkedHashMap<>();
        for (VocabularyCardGenerationJobItem item : items) {
            item.setAttemptCount(value(item.getAttemptCount()) + CommonConstants.SEQUENCE_STEP);
            item.setErrorMessage(null);
            EnglishVocabularyStudyRecord record = cached.get(item.getNormalizedTerm());
            if (record == null) {
                item.setStatus(VocabularyCardConstants.ITEM_PENDING);
                misses.add(item);
            } else {
                cacheHits.put(item, record);
            }
            item.setUpdateTime(now);
        }
        if (!misses.isEmpty()) {
            itemMapper.updateBatch(misses);
        }
        attachResults(userId, cacheHits, VocabularyCardConstants.ITEM_CACHE_HIT);
        refreshJobProgress(job, false);
        if (isCancelled(job)) {
            cancelJob(job);
            return;
        }

        int batchSize = resolveBatchSize(job.getBatchSize());
        for (int offset = 0; offset < misses.size(); offset += batchSize) {
            List<VocabularyCardGenerationJobItem> batch = misses.subList(offset, Math.min(misses.size(), offset + batchSize));
            updateItemStatuses(batch, VocabularyCardConstants.ITEM_GENERATING, null);
            try {
                AgentChatResponse response = requestBatch(userId, batch, modelConfigId);
                Map<String, JsonNode> cards = parseCards(response);
                List<VocabularyCardGenerationJobItem> failedItems = new ArrayList<>();
                List<VocabularyCardGenerationJobItem> successfulItems = new ArrayList<>();
                Map<VocabularyCardGenerationJobItem, JsonNode> generatedCards = new LinkedHashMap<>();
                for (VocabularyCardGenerationJobItem item : batch) {
                    JsonNode card = cards.get(item.getNormalizedTerm());
                    if (card == null) {
                        item.setErrorMessage("模型响应中缺少该词卡");
                        failedItems.add(item);
                        continue;
                    }
                    generatedCards.put(item, card);
                    successfulItems.add(item);
                }
                updateItemStatuses(failedItems, VocabularyCardConstants.ITEM_FAILED, null);
                try {
                    Map<VocabularyCardGenerationJobItem, EnglishVocabularyStudyRecord> persisted =
                            saveCards(generatedCards, response, batch.size());
                    attachResults(userId, persisted, VocabularyCardConstants.ITEM_COMPLETED);
                    List<VocabularyCardGenerationJobItem> persistenceFailures = successfulItems.stream()
                            .filter(item -> !persisted.containsKey(item))
                            .toList();
                    updateItemStatuses(persistenceFailures, VocabularyCardConstants.ITEM_FAILED,
                            "词卡保存后未能读取持久化记录");
                } catch (RuntimeException ex) {
                    log.debug("批量词卡保存失败 jobId={} batchOffset={} error={}", job.getId(), offset, ex.getMessage());
                    updateItemStatuses(successfulItems, VocabularyCardConstants.ITEM_FAILED, ex.getMessage());
                }
            } catch (RuntimeException ex) {
                log.debug("批量词卡调用失败 jobId={} batchOffset={} error={}", job.getId(), offset, ex.getMessage());
                updateItemStatuses(batch, VocabularyCardConstants.ITEM_FAILED, ex.getMessage());
            }
            refreshJobProgress(job, false);
            if (isCancelled(job)) {
                cancelJob(job);
                return;
            }
        }
        finishJob(job);
    }

    private AgentChatResponse requestBatch(Long userId, List<VocabularyCardGenerationJobItem> batch, Long modelConfigId) {
        List<String> terms = batch.stream().map(VocabularyCardGenerationJobItem::getTerm).toList();
        Map<String, Object> variables = new HashMap<>();
        variables.put("terms", terms);
        AgentChatRequest request = new AgentChatRequest();
        request.setUserId(userId);
        request.setInvocationScene(AiInvocationScene.VOCABULARY_CARD_BATCH);
        request.setAgentCode(AiScenarioConstants.VOCABULARY_AGENT_CODE);
        request.setTemplateCode(AiScenarioConstants.VOCABULARY_BATCH_TEMPLATE_CODE);
        request.setTitle(LearningScene.ENGLISH_VOCABULARY.getTitle());
        request.setBusinessType(AiChatConstants.BUSINESS_TYPE_LEARNING);
        request.setBusinessId(LearningScene.ENGLISH_VOCABULARY.getCode());
        request.setSceneCode(LearningScene.ENGLISH_VOCABULARY.getCode());
        request.setModelConfigId(modelConfigId);
        request.setMessage("请批量生成这 " + terms.size() + " 个词的结构化学习卡片。");
        request.setVariables(variables);
        return aiChatService.chat(request);
    }

    private Map<VocabularyCardGenerationJobItem, EnglishVocabularyStudyRecord> saveCards(
            Map<VocabularyCardGenerationJobItem, JsonNode> generatedCards,
            AgentChatResponse response, int batchSize) {
        if (generatedCards.isEmpty()) {
            return Map.of();
        }
        LocalDateTime now = LocalDateTime.now();
        List<EnglishVocabularyStudyRecord> records = new ArrayList<>();
        for (Map.Entry<VocabularyCardGenerationJobItem, JsonNode> generated : generatedCards.entrySet()) {
            VocabularyCardGenerationJobItem item = generated.getKey();
            JsonNode card = generated.getValue();
            EnglishVocabularyStudyRecord record = new EnglishVocabularyStudyRecord();
            record.setId(com.baomidou.mybatisplus.core.toolkit.IdWorker.getId());
            record.setTerm(text(card, "term") == null ? item.getTerm() : text(card, "term"));
            record.setNormalizedTerm(item.getNormalizedTerm());
            record.setAgentCode(AiScenarioConstants.VOCABULARY_AGENT_CODE);
            record.setTemplateCode(AiScenarioConstants.VOCABULARY_BATCH_TEMPLATE_CODE);
            record.setProvider(response.getModelProvider());
            record.setModelName(response.getModelName());
            record.setSessionId(response.getSessionId());
            record.setRawContent(writeJson(card));
            record.setParsedJson(writeJson(card));
            record.setTokenUsage(response.getTokenUsage() == null ? null : response.getTokenUsage() / Math.max(1, batchSize));
            record.setCostTime(response.getCostTime() == null ? null : response.getCostTime() / Math.max(1, batchSize));
            record.setLookupCount(VocabularyConstants.DEFAULT_LOOKUP_COUNT);
            record.setLastLookupTime(now);
            record.setDeleted(false);
            record.setCreateBy(0L);
            record.setUpdateBy(0L);
            record.setVersion(CommonConstants.ZERO);
            record.setCreateTime(now);
            record.setUpdateTime(now);
            records.add(record);
        }
        for (int start = 0; start < records.size(); start += 200) {
            vocabularyMapper.insertBatchIgnore(records.subList(start, Math.min(start + 200, records.size())));
        }
        Map<String, EnglishVocabularyStudyRecord> byTerm = findCached(new ArrayList<>(generatedCards.keySet()));
        insightService.syncInsightsBatch(new ArrayList<>(byTerm.values()));
        Map<VocabularyCardGenerationJobItem, EnglishVocabularyStudyRecord> result = new LinkedHashMap<>();
        for (VocabularyCardGenerationJobItem item : generatedCards.keySet()) {
            EnglishVocabularyStudyRecord record = byTerm.get(item.getNormalizedTerm());
            if (record != null) {
                result.put(item, record);
            }
        }
        return result;
    }

    private void attachResults(Long userId,
                               Map<VocabularyCardGenerationJobItem, EnglishVocabularyStudyRecord> results,
                               String itemStatus) {
        if (results == null || results.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        Map<Long, EnglishVocabularyStudyRecord> cardsByEntryId = new LinkedHashMap<>();
        List<Long> progressIds = new ArrayList<>();
        List<VocabularyCardGenerationJobItem> items = new ArrayList<>();
        for (Map.Entry<VocabularyCardGenerationJobItem, EnglishVocabularyStudyRecord> result : results.entrySet()) {
            VocabularyCardGenerationJobItem item = result.getKey();
            EnglishVocabularyStudyRecord record = result.getValue();
            if (item.getWordbookEntryId() != null) {
                cardsByEntryId.put(item.getWordbookEntryId(), record);
            }
            item.setVocabularyId(record.getId());
            item.setStatus(itemStatus);
            item.setErrorMessage(null);
            item.setUpdateTime(now);
            items.add(item);
            if (item.getWordProgressId() != null) {
                progressIds.add(item.getWordProgressId());
            }
        }
        wordbookService.attachVocabularyCards(userId, cardsByEntryId);
        itemMapper.updateBatch(items);
        if (!progressIds.isEmpty()) {
            progressMapper.updateCardStatusBatch(progressIds.stream().distinct().toList(),
                    VocabularyCardConstants.STATUS_READY, now);
        }
    }

    private Map<String, EnglishVocabularyStudyRecord> findCached(List<VocabularyCardGenerationJobItem> items) {
        Set<String> terms = items.stream().map(VocabularyCardGenerationJobItem::getNormalizedTerm).collect(Collectors.toSet());
        if (terms.isEmpty()) {
            return Map.of();
        }
        return vocabularyMapper.selectList(new LambdaQueryWrapper<EnglishVocabularyStudyRecord>()
                        .in(EnglishVocabularyStudyRecord::getNormalizedTerm, terms)
                        .eq(EnglishVocabularyStudyRecord::getDeleted, false))
                .stream()
                .collect(Collectors.toMap(EnglishVocabularyStudyRecord::getNormalizedTerm, record -> record,
                        (left, right) -> left));
    }

    private Map<String, JsonNode> parseCards(AgentChatResponse response) {
        JsonNode root = response.requireStructuredRoot(AiInvocationScene.VOCABULARY_CARD_BATCH);
        JsonNode cards = root.path("cards");
        if (!cards.isArray()) {
            throw LearningAssistantException.badRequest(
                    LearningErrorCode.AI_RESPONSE_PARSE_FAILED,
                    "批量词卡响应缺少 cards 数组");
        }
        Map<String, JsonNode> result = new LinkedHashMap<>();
        for (JsonNode card : cards) {
            String term = text(card, "term");
            if (StringUtils.hasText(term)) {
                result.put(normalize(term), card);
            }
        }
        return result;
    }

    private void finishJob(VocabularyCardGenerationJob job) {
        refreshJobProgress(job, true);
    }

    /** 每个 AI 批次结束后同步领域任务和任务中心进度。 */
    private void refreshJobProgress(VocabularyCardGenerationJob job, boolean finished) {
        VocabularyCardGenerationProgress progress = itemMapper.selectProgress(job.getId());
        int total = progress == null ? value(job.getTotalCount()) : value(progress.getTotalCount());
        int success = progress == null ? value(job.getSuccessCount()) : value(progress.getSuccessCount());
        int failed = progress == null ? value(job.getFailedCount()) : value(progress.getFailedCount());
        job.setTotalCount(total);
        job.setSuccessCount(success);
        job.setFailedCount(failed);
        if (finished) {
            job.setStatus(failed == CommonConstants.ZERO
                    ? VocabularyCardConstants.JOB_COMPLETED
                    : success == CommonConstants.ZERO
                    ? VocabularyCardConstants.JOB_FAILED
                    : VocabularyCardConstants.JOB_PARTIAL_FAILED);
            job.setFinishedTime(LocalDateTime.now());
        }
        job.setUpdateTime(LocalDateTime.now());
        jobMapper.updateById(job);
        if (job.getAsyncTaskId() != null) {
            aiAsyncTaskService.updateProgress(job.getAsyncTaskId(), total, success, failed);
        }
    }

    private boolean isCancelled(VocabularyCardGenerationJob job) {
        return job.getAsyncTaskId() != null && aiAsyncTaskService.isCancelled(job.getAsyncTaskId());
    }

    private void cancelJob(VocabularyCardGenerationJob job) {
        job.setStatus(VocabularyCardConstants.JOB_CANCELLED);
        job.setFinishedTime(LocalDateTime.now());
        job.setUpdateTime(LocalDateTime.now());
        jobMapper.updateById(job);
    }

    /** 批量刷新一批词卡任务状态，避免状态变更逐词发送 UPDATE。 */
    private void updateItemStatuses(List<VocabularyCardGenerationJobItem> items, String status, String error) {
        if (items == null || items.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (VocabularyCardGenerationJobItem item : items) {
            item.setStatus(status);
            item.setErrorMessage(limitError(error));
            item.setUpdateTime(now);
        }
        itemMapper.updateBatch(items);
        List<Long> progressIds = items.stream()
                .map(VocabularyCardGenerationJobItem::getWordProgressId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        if (!progressIds.isEmpty()) {
            progressMapper.updateCardStatusBatch(
                    progressIds,
                    VocabularyCardConstants.ITEM_FAILED.equals(status)
                            ? VocabularyCardConstants.STATUS_FAILED
                            : VocabularyCardConstants.STATUS_GENERATING,
                    now);
        }
    }

    private VocabularyCardGenerationResponse toResponse(VocabularyCardGenerationJob job) {
        return toResponse(job, 1, VocabularyCardConstants.DEFAULT_ITEM_PAGE_SIZE);
    }

    private VocabularyCardGenerationResponse toResponse(VocabularyCardGenerationJob job, int page, int pageSize) {
        VocabularyCardGenerationResponse response = new VocabularyCardGenerationResponse();
        response.setJobId(job.getId());
        response.setPlanId(job.getPlanId());
        response.setUnitId(job.getUnitId());
        response.setAsyncTaskId(job.getAsyncTaskId());
        response.setStatus(job.getStatus());
        response.setBatchSize(job.getBatchSize());
        response.setTotalCount(job.getTotalCount());
        response.setSuccessCount(job.getSuccessCount());
        response.setFailedCount(job.getFailedCount());
        response.setErrorMessage(job.getErrorMessage());
        Page<VocabularyCardGenerationJobItem> itemPage = itemMapper.selectPage(new Page<>(page, pageSize),
                new LambdaQueryWrapper<VocabularyCardGenerationJobItem>()
                        .eq(VocabularyCardGenerationJobItem::getJobId, job.getId())
                        .eq(VocabularyCardGenerationJobItem::getDeleted, false)
                        .orderByAsc(VocabularyCardGenerationJobItem::getCreateTime));
        response.setItemPage(page);
        response.setItemPageSize(pageSize);
        response.setItemTotal(itemPage.getTotal());
        response.setItemHasMore((long) page * pageSize < itemPage.getTotal());
        response.setItems(itemPage.getRecords().stream().map(this::toItemResponse).toList());
        response.setStartedTime(job.getStartedTime());
        response.setFinishedTime(job.getFinishedTime());
        return response;
    }

    private VocabularyCardGenerationItemResponse toItemResponse(VocabularyCardGenerationJobItem item) {
        VocabularyCardGenerationItemResponse response = new VocabularyCardGenerationItemResponse();
        response.setId(item.getId());
        response.setTerm(item.getTerm());
        response.setNormalizedTerm(item.getNormalizedTerm());
        response.setStatus(item.getStatus());
        response.setVocabularyId(item.getVocabularyId());
        response.setAttemptCount(item.getAttemptCount());
        response.setErrorMessage(item.getErrorMessage());
        return response;
    }

    private VocabularyCardGenerationJob requireJob(Long userId, Long jobId) {
        VocabularyCardGenerationJob job = jobMapper.selectOne(new LambdaQueryWrapper<VocabularyCardGenerationJob>()
                .eq(VocabularyCardGenerationJob::getId, jobId)
                .eq(VocabularyCardGenerationJob::getUserId, userId)
                .eq(VocabularyCardGenerationJob::getDeleted, false)
                .last(CommonConstants.SQL_LIMIT_ONE));
        if (job == null) {
            throw LearningAssistantException.notFound(
                    LearningErrorCode.VOCABULARY_RECORD_NOT_FOUND,
                    "词卡生成任务不存在: " + jobId);
        }
        return job;
    }

    private int resolveBatchSize(Integer value) {
        int resolved = value == null ? VocabularyCardConstants.DEFAULT_BATCH_SIZE : value;
        return Math.max(VocabularyCardConstants.MIN_BATCH_SIZE,
                Math.min(resolved, VocabularyCardConstants.MAX_BATCH_SIZE));
    }

    private String text(JsonNode node, String key) {
        JsonNode value = node == null ? null : node.path(key);
        return value != null && value.isTextual() && StringUtils.hasText(value.asText())
                ? value.asText().trim()
                : null;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw LearningAssistantException.system(
                    LearningErrorCode.JSON_SERIALIZE_FAILED,
                    "批量词卡序列化失败",
                    ex);
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private int value(Integer value) {
        return value == null ? CommonConstants.ZERO : value;
    }

    private String limitError(String error) {
        if (!StringUtils.hasText(error)) {
            return null;
        }
        return error.length() <= 1000 ? error : error.substring(0, 1000);
    }

    private record GenerationSubmission(VocabularyCardGenerationJob job, int itemCount, boolean existing) {
    }
}
