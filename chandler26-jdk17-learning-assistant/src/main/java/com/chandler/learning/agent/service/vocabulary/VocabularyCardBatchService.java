package com.chandler.learning.agent.service.vocabulary;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chandler.learning.agent.domain.dto.AgentChatRequest;
import com.chandler.learning.agent.domain.dto.AgentChatResponse;
import com.chandler.learning.agent.domain.dto.vocabulary.VocabularyCardGenerationItemResponse;
import com.chandler.learning.agent.domain.dto.vocabulary.VocabularyCardGenerationRequest;
import com.chandler.learning.agent.domain.dto.vocabulary.VocabularyCardGenerationResponse;
import com.chandler.learning.agent.domain.entity.learning.LearningPlan;
import com.chandler.learning.agent.domain.entity.learning.LearningPlanUnit;
import com.chandler.learning.agent.domain.entity.learning.LearningPlanUnitEntry;
import com.chandler.learning.agent.domain.entity.learning.LearningWordProgress;
import com.chandler.learning.agent.domain.entity.vocabulary.EnglishVocabularyStudyRecord;
import com.chandler.learning.agent.domain.entity.vocabulary.VocabularyCardGenerationJob;
import com.chandler.learning.agent.domain.entity.vocabulary.VocabularyCardGenerationJobItem;
import com.chandler.learning.agent.domain.enums.LearningScene;
import com.chandler.learning.agent.domain.enums.SystemLogType;
import com.chandler.learning.agent.exception.LearningAssistantException;
import com.chandler.learning.agent.mapper.learning.LearningPlanMapper;
import com.chandler.learning.agent.mapper.learning.LearningPlanUnitEntryMapper;
import com.chandler.learning.agent.mapper.learning.LearningPlanUnitMapper;
import com.chandler.learning.agent.mapper.learning.LearningWordProgressMapper;
import com.chandler.learning.agent.mapper.vocabulary.EnglishVocabularyStudyRecordMapper;
import com.chandler.learning.agent.mapper.vocabulary.VocabularyCardGenerationJobItemMapper;
import com.chandler.learning.agent.mapper.vocabulary.VocabularyCardGenerationJobMapper;
import com.chandler.learning.agent.service.AiChatService;
import com.chandler.learning.agent.service.learning.SystemLogService;
import com.chandler.learning.agent.service.learning.UserDisplayNameService;
import com.chandler.learning.agent.service.learning.VocabularyInsightService;
import com.chandler.learning.agent.service.learning.WordbookService;
import com.chandler.learning.agent.support.LearningConstants;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 对当前场景的必要词卡先查共享缓存，再将缺失项按 10-20 个一批生成。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VocabularyCardBatchService {

    private static final Pattern JSON_BLOCK_PATTERN = Pattern.compile("\\{[\\s\\S]*}");

    private final VocabularyCardGenerationJobMapper jobMapper;
    private final VocabularyCardGenerationJobItemMapper itemMapper;
    private final EnglishVocabularyStudyRecordMapper vocabularyMapper;
    private final LearningPlanMapper planMapper;
    private final LearningPlanUnitMapper unitMapper;
    private final LearningPlanUnitEntryMapper unitEntryMapper;
    private final LearningWordProgressMapper progressMapper;
    private final AiChatService aiChatService;
    private final WordbookService wordbookService;
    private final VocabularyInsightService insightService;
    private final SystemLogService systemLogService;
    private final UserDisplayNameService userDisplayNameService;
    private final ObjectMapper objectMapper;

    /**
     * 为一个场景中的核心和复习词创建同步批任务。
     */
    @Transactional(rollbackFor = Exception.class)
    public VocabularyCardGenerationResponse generate(Long userId, Long planId, Long unitId,
                                                     VocabularyCardGenerationRequest request) {
        LearningPlan plan = requirePlan(userId, planId);
        requireUnit(plan, unitId);
        int batchSize = resolveBatchSize(request == null ? null : request.getBatchSize());
        List<LearningPlanUnitEntry> unitEntries = unitEntryMapper.selectList(
                new LambdaQueryWrapper<LearningPlanUnitEntry>()
                        .eq(LearningPlanUnitEntry::getUnitId, unitId)
                        .in(LearningPlanUnitEntry::getTier,
                                List.of(LearningConstants.ScenePlan.TIER_CORE, LearningConstants.ScenePlan.TIER_REVIEW))
                        .isNotNull(LearningPlanUnitEntry::getWordbookEntryId)
                        .eq(LearningPlanUnitEntry::getDeleted, false)
                        .orderByAsc(LearningPlanUnitEntry::getSortOrder));
        Map<String, LearningPlanUnitEntry> unique = unitEntries.stream().collect(Collectors.toMap(
                LearningPlanUnitEntry::getNormalizedTerm, entry -> entry,
                (left, right) -> left, LinkedHashMap::new));
        LocalDateTime now = LocalDateTime.now();
        VocabularyCardGenerationJob job = new VocabularyCardGenerationJob();
        job.setUserId(userId);
        job.setPlanId(planId);
        job.setUnitId(unitId);
        job.setStatus(LearningConstants.VocabularyCard.JOB_PENDING);
        job.setBatchSize(batchSize);
        job.setTotalCount(unique.size());
        job.setSuccessCount(LearningConstants.ZERO);
        job.setFailedCount(LearningConstants.ZERO);
        job.setDeleted(false);
        job.setCreateTime(now);
        job.setUpdateTime(now);
        jobMapper.insert(job);

        List<VocabularyCardGenerationJobItem> items = new ArrayList<>();
        for (LearningPlanUnitEntry entry : unique.values()) {
            VocabularyCardGenerationJobItem item = new VocabularyCardGenerationJobItem();
            item.setJobId(job.getId());
            item.setWordProgressId(entry.getWordProgressId());
            item.setWordbookEntryId(entry.getWordbookEntryId());
            item.setTerm(entry.getTerm());
            item.setNormalizedTerm(entry.getNormalizedTerm());
            item.setStatus(LearningConstants.VocabularyCard.ITEM_PENDING);
            item.setAttemptCount(LearningConstants.ZERO);
            item.setDeleted(false);
            item.setCreateTime(now);
            item.setUpdateTime(now);
            itemMapper.insert(item);
            items.add(item);
        }
        process(userId, job, items, request == null ? null : request.getModelConfigId());
        systemLogService.record(userId, SystemLogType.AI, "批量生成场景词卡",
                plan.getName() + "，共 " + items.size() + " 个词");
        log.info("用户「{}」为计划「{}」的场景 {} 发起批量词卡任务，共 {} 个去重词，批大小 {}",
                userDisplayNameService.userName(userId), plan.getName(), unitId, items.size(), batchSize);
        return toResponse(jobMapper.selectById(job.getId()));
    }

    /**
     * 仅重试一个任务中上次失败的单词。
     */
    @Transactional(rollbackFor = Exception.class)
    public VocabularyCardGenerationResponse retryFailed(Long userId, Long jobId,
                                                        VocabularyCardGenerationRequest request) {
        VocabularyCardGenerationJob job = requireJob(userId, jobId);
        List<VocabularyCardGenerationJobItem> failed = itemMapper.selectList(
                new LambdaQueryWrapper<VocabularyCardGenerationJobItem>()
                        .eq(VocabularyCardGenerationJobItem::getJobId, jobId)
                        .eq(VocabularyCardGenerationJobItem::getStatus, LearningConstants.VocabularyCard.ITEM_FAILED)
                        .eq(VocabularyCardGenerationJobItem::getDeleted, false));
        if (request != null && request.getBatchSize() != null) {
            job.setBatchSize(resolveBatchSize(request.getBatchSize()));
        }
        process(userId, job, failed, request == null ? null : request.getModelConfigId());
        return toResponse(jobMapper.selectById(jobId));
    }

    public VocabularyCardGenerationResponse detail(Long userId, Long jobId) {
        return toResponse(requireJob(userId, jobId));
    }

    private void process(Long userId, VocabularyCardGenerationJob job,
                         List<VocabularyCardGenerationJobItem> items, Long modelConfigId) {
        LocalDateTime now = LocalDateTime.now();
        job.setStatus(LearningConstants.VocabularyCard.JOB_RUNNING);
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
        for (VocabularyCardGenerationJobItem item : items) {
            item.setAttemptCount(value(item.getAttemptCount()) + LearningConstants.SEQUENCE_STEP);
            item.setErrorMessage(null);
            EnglishVocabularyStudyRecord record = cached.get(item.getNormalizedTerm());
            if (record == null) {
                item.setStatus(LearningConstants.VocabularyCard.ITEM_PENDING);
                misses.add(item);
            } else {
                attachResult(userId, item, record, LearningConstants.VocabularyCard.ITEM_CACHE_HIT);
            }
            item.setUpdateTime(LocalDateTime.now());
            itemMapper.updateById(item);
        }

        int batchSize = resolveBatchSize(job.getBatchSize());
        for (int offset = 0; offset < misses.size(); offset += batchSize) {
            List<VocabularyCardGenerationJobItem> batch = misses.subList(offset, Math.min(misses.size(), offset + batchSize));
            batch.forEach(item -> updateItemStatus(item, LearningConstants.VocabularyCard.ITEM_GENERATING, null));
            try {
                AgentChatResponse response = requestBatch(batch, modelConfigId);
                Map<String, JsonNode> cards = parseCards(response.getContent());
                for (VocabularyCardGenerationJobItem item : batch) {
                    JsonNode card = cards.get(item.getNormalizedTerm());
                    if (card == null) {
                        updateItemStatus(item, LearningConstants.VocabularyCard.ITEM_FAILED,
                                "模型响应中缺少该词卡");
                        continue;
                    }
                    try {
                        EnglishVocabularyStudyRecord record = saveCard(item, card, response, batch.size());
                        attachResult(userId, item, record, LearningConstants.VocabularyCard.ITEM_COMPLETED);
                    } catch (RuntimeException ex) {
                        log.debug("批量词卡单词保存失败 term={} error={}", item.getNormalizedTerm(), ex.getMessage());
                        updateItemStatus(item, LearningConstants.VocabularyCard.ITEM_FAILED, ex.getMessage());
                    }
                }
            } catch (RuntimeException ex) {
                log.debug("批量词卡调用失败 jobId={} batchOffset={} error={}", job.getId(), offset, ex.getMessage());
                batch.forEach(item -> updateItemStatus(item, LearningConstants.VocabularyCard.ITEM_FAILED, ex.getMessage()));
            }
        }
        finishJob(job);
    }

    private AgentChatResponse requestBatch(List<VocabularyCardGenerationJobItem> batch, Long modelConfigId) {
        List<String> terms = batch.stream().map(VocabularyCardGenerationJobItem::getTerm).toList();
        Map<String, Object> variables = new HashMap<>();
        variables.put("terms", terms);
        AgentChatRequest request = new AgentChatRequest();
        request.setAgentCode(LearningConstants.VOCABULARY_AGENT_CODE);
        request.setTemplateCode(LearningConstants.VOCABULARY_BATCH_TEMPLATE_CODE);
        request.setTitle(LearningScene.ENGLISH_VOCABULARY.getTitle());
        request.setBusinessType(LearningConstants.ChatSession.BUSINESS_TYPE_LEARNING);
        request.setBusinessId(LearningScene.ENGLISH_VOCABULARY.getCode());
        request.setSceneCode(LearningScene.ENGLISH_VOCABULARY.getCode());
        request.setModelConfigId(modelConfigId);
        request.setMessage("请批量生成这 " + terms.size() + " 个词的结构化学习卡片。");
        request.setVariables(variables);
        return aiChatService.chat(request);
    }

    private EnglishVocabularyStudyRecord saveCard(VocabularyCardGenerationJobItem item, JsonNode card,
                                                  AgentChatResponse response, int batchSize) {
        EnglishVocabularyStudyRecord existing = findVocabulary(item.getNormalizedTerm());
        if (existing != null) {
            return existing;
        }
        LocalDateTime now = LocalDateTime.now();
        EnglishVocabularyStudyRecord record = new EnglishVocabularyStudyRecord();
        record.setTerm(text(card, "term") == null ? item.getTerm() : text(card, "term"));
        record.setNormalizedTerm(item.getNormalizedTerm());
        record.setAgentCode(LearningConstants.VOCABULARY_AGENT_CODE);
        record.setTemplateCode(LearningConstants.VOCABULARY_BATCH_TEMPLATE_CODE);
        record.setProvider(response.getModelProvider());
        record.setModelName(response.getModelName());
        record.setSessionId(response.getSessionId());
        record.setRawContent(writeJson(card));
        record.setParsedJson(writeJson(card));
        record.setTokenUsage(response.getTokenUsage() == null ? null : response.getTokenUsage() / Math.max(1, batchSize));
        record.setCostTime(response.getCostTime() == null ? null : response.getCostTime() / Math.max(1, batchSize));
        record.setLookupCount(LearningConstants.Vocabulary.DEFAULT_LOOKUP_COUNT);
        record.setLastLookupTime(now);
        record.setDeleted(false);
        record.setCreateTime(now);
        record.setUpdateTime(now);
        try {
            vocabularyMapper.insert(record);
        } catch (DuplicateKeyException ex) {
            record = findVocabulary(item.getNormalizedTerm());
        }
        insightService.syncInsights(record);
        return record;
    }

    private void attachResult(Long userId, VocabularyCardGenerationJobItem item,
                              EnglishVocabularyStudyRecord record, String itemStatus) {
        wordbookService.attachVocabularyCard(userId, item.getWordbookEntryId(), record);
        item.setVocabularyId(record.getId());
        item.setStatus(itemStatus);
        item.setErrorMessage(null);
        item.setUpdateTime(LocalDateTime.now());
        itemMapper.updateById(item);
        LearningWordProgress progress = progressMapper.selectById(item.getWordProgressId());
        if (progress != null) {
            progress.setCardStatus(LearningConstants.VocabularyCard.STATUS_READY);
            progress.setUpdateTime(LocalDateTime.now());
            progressMapper.updateById(progress);
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

    private Map<String, JsonNode> parseCards(String content) {
        JsonNode root = parseJson(content);
        JsonNode cards = root.path("cards");
        if (!cards.isArray()) {
            throw LearningAssistantException.badRequest(
                    LearningConstants.ErrorCode.AI_RESPONSE_PARSE_FAILED,
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

    private JsonNode parseJson(String content) {
        String cleaned = content == null ? "" : content.replace("```json", "").replace("```", "").trim();
        try {
            return objectMapper.readTree(cleaned);
        } catch (Exception ignored) {
            Matcher matcher = JSON_BLOCK_PATTERN.matcher(cleaned);
            if (matcher.find()) {
                try {
                    return objectMapper.readTree(matcher.group());
                } catch (Exception ex) {
                    log.debug("批量词卡 JSON 二次提取失败 error={}", ex.getMessage());
                }
            }
            throw LearningAssistantException.badRequest(
                    LearningConstants.ErrorCode.AI_RESPONSE_PARSE_FAILED,
                    "批量词卡响应不是有效 JSON");
        }
    }

    private void finishJob(VocabularyCardGenerationJob job) {
        List<VocabularyCardGenerationJobItem> all = itemMapper.selectList(
                new LambdaQueryWrapper<VocabularyCardGenerationJobItem>()
                        .eq(VocabularyCardGenerationJobItem::getJobId, job.getId())
                        .eq(VocabularyCardGenerationJobItem::getDeleted, false));
        int success = (int) all.stream().filter(item -> LearningConstants.VocabularyCard.ITEM_COMPLETED.equals(item.getStatus())
                || LearningConstants.VocabularyCard.ITEM_CACHE_HIT.equals(item.getStatus())).count();
        int failed = (int) all.stream().filter(item -> LearningConstants.VocabularyCard.ITEM_FAILED.equals(item.getStatus())).count();
        job.setSuccessCount(success);
        job.setFailedCount(failed);
        job.setStatus(failed == LearningConstants.ZERO
                ? LearningConstants.VocabularyCard.JOB_COMPLETED
                : success == LearningConstants.ZERO
                ? LearningConstants.VocabularyCard.JOB_FAILED
                : LearningConstants.VocabularyCard.JOB_PARTIAL_FAILED);
        job.setFinishedTime(LocalDateTime.now());
        job.setUpdateTime(LocalDateTime.now());
        jobMapper.updateById(job);
    }

    private void updateItemStatus(VocabularyCardGenerationJobItem item, String status, String error) {
        item.setStatus(status);
        item.setErrorMessage(limitError(error));
        item.setUpdateTime(LocalDateTime.now());
        itemMapper.updateById(item);
        LearningWordProgress progress = progressMapper.selectById(item.getWordProgressId());
        if (progress != null) {
            progress.setCardStatus(LearningConstants.VocabularyCard.ITEM_FAILED.equals(status)
                    ? LearningConstants.VocabularyCard.STATUS_FAILED
                    : LearningConstants.VocabularyCard.STATUS_GENERATING);
            progress.setUpdateTime(LocalDateTime.now());
            progressMapper.updateById(progress);
        }
    }

    private VocabularyCardGenerationResponse toResponse(VocabularyCardGenerationJob job) {
        VocabularyCardGenerationResponse response = new VocabularyCardGenerationResponse();
        response.setJobId(job.getId());
        response.setPlanId(job.getPlanId());
        response.setUnitId(job.getUnitId());
        response.setStatus(job.getStatus());
        response.setBatchSize(job.getBatchSize());
        response.setTotalCount(job.getTotalCount());
        response.setSuccessCount(job.getSuccessCount());
        response.setFailedCount(job.getFailedCount());
        response.setItems(itemMapper.selectList(new LambdaQueryWrapper<VocabularyCardGenerationJobItem>()
                        .eq(VocabularyCardGenerationJobItem::getJobId, job.getId())
                        .eq(VocabularyCardGenerationJobItem::getDeleted, false)
                        .orderByAsc(VocabularyCardGenerationJobItem::getCreateTime))
                .stream().map(this::toItemResponse).toList());
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

    private LearningPlan requirePlan(Long userId, Long planId) {
        LearningPlan plan = planMapper.selectOne(new LambdaQueryWrapper<LearningPlan>()
                .eq(LearningPlan::getId, planId)
                .eq(LearningPlan::getUserId, userId)
                .eq(LearningPlan::getDeleted, false)
                .last(LearningConstants.SQL_LIMIT_ONE));
        if (plan == null) {
            throw LearningAssistantException.notFound(
                    LearningConstants.ErrorCode.LEARNING_PLAN_NOT_FOUND,
                    "学习计划不存在: " + planId);
        }
        return plan;
    }

    private LearningPlanUnit requireUnit(LearningPlan plan, Long unitId) {
        LearningPlanUnit unit = unitMapper.selectOne(new LambdaQueryWrapper<LearningPlanUnit>()
                .eq(LearningPlanUnit::getId, unitId)
                .eq(LearningPlanUnit::getPlanId, plan.getId())
                .eq(LearningPlanUnit::getDeleted, false)
                .last(LearningConstants.SQL_LIMIT_ONE));
        if (unit == null) {
            throw LearningAssistantException.notFound(
                    LearningConstants.ErrorCode.LEARNING_PLAN_UNIT_NOT_FOUND,
                    "场景学习单元不存在: " + unitId);
        }
        return unit;
    }

    private VocabularyCardGenerationJob requireJob(Long userId, Long jobId) {
        VocabularyCardGenerationJob job = jobMapper.selectOne(new LambdaQueryWrapper<VocabularyCardGenerationJob>()
                .eq(VocabularyCardGenerationJob::getId, jobId)
                .eq(VocabularyCardGenerationJob::getUserId, userId)
                .eq(VocabularyCardGenerationJob::getDeleted, false)
                .last(LearningConstants.SQL_LIMIT_ONE));
        if (job == null) {
            throw LearningAssistantException.notFound(
                    LearningConstants.ErrorCode.VOCABULARY_RECORD_NOT_FOUND,
                    "词卡生成任务不存在: " + jobId);
        }
        return job;
    }

    private EnglishVocabularyStudyRecord findVocabulary(String normalizedTerm) {
        return vocabularyMapper.selectOne(new LambdaQueryWrapper<EnglishVocabularyStudyRecord>()
                .eq(EnglishVocabularyStudyRecord::getNormalizedTerm, normalizedTerm)
                .eq(EnglishVocabularyStudyRecord::getDeleted, false)
                .last(LearningConstants.SQL_LIMIT_ONE));
    }

    private int resolveBatchSize(Integer value) {
        int resolved = value == null ? LearningConstants.VocabularyCard.DEFAULT_BATCH_SIZE : value;
        return Math.max(LearningConstants.VocabularyCard.MIN_BATCH_SIZE,
                Math.min(resolved, LearningConstants.VocabularyCard.MAX_BATCH_SIZE));
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
                    LearningConstants.ErrorCode.JSON_SERIALIZE_FAILED,
                    "批量词卡序列化失败",
                    ex);
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private int value(Integer value) {
        return value == null ? LearningConstants.ZERO : value;
    }

    private String limitError(String error) {
        if (!StringUtils.hasText(error)) {
            return null;
        }
        return error.length() <= 1000 ? error : error.substring(0, 1000);
    }
}
