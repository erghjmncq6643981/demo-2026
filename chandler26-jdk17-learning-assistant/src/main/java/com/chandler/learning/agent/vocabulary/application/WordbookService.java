package com.chandler.learning.agent.vocabulary.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chandler.learning.agent.vocabulary.api.request.AddWordbookEntryRequest;
import com.chandler.learning.agent.identity.api.response.LearningActivityDayResponse;
import com.chandler.learning.agent.identity.api.response.LearningActivityResponse;
import com.chandler.learning.agent.vocabulary.api.request.ReviewSubmitRequest;
import com.chandler.learning.agent.vocabulary.api.response.ReviewSubmitResponse;
import com.chandler.learning.agent.vocabulary.api.response.VocabularyRelationResponse;
import com.chandler.learning.agent.vocabulary.api.response.VocabularyTagResponse;
import com.chandler.learning.agent.vocabulary.api.request.WordbookEntryTransferRequest;
import com.chandler.learning.agent.vocabulary.api.response.WordbookEntryResponse;
import com.chandler.learning.agent.vocabulary.api.response.WordbookEntryPageResponse;
import com.chandler.learning.agent.vocabulary.api.response.WordbookEntrySummaryResponse;
import com.chandler.learning.agent.vocabulary.api.request.WordbookEntryUpdateRequest;
import com.chandler.learning.agent.vocabulary.api.response.WordbookResponse;
import com.chandler.learning.agent.vocabulary.api.request.WordbookSaveRequest;
import com.chandler.learning.agent.vocabulary.api.request.VocabularyStudyRequest;
import com.chandler.learning.agent.vocabulary.api.response.VocabularyStudyResponse;
import com.chandler.learning.agent.learning.domain.enums.ReviewResult;
import com.chandler.learning.agent.learning.domain.enums.ReviewStatus;
import com.chandler.learning.agent.system.domain.enums.SystemLogType;
import com.chandler.learning.agent.vocabulary.domain.entity.LearningWordbook;
import com.chandler.learning.agent.vocabulary.domain.entity.LearningWordbookEntry;
import com.chandler.learning.agent.vocabulary.domain.entity.EnglishVocabularyStudyRecord;
import com.chandler.learning.agent.exception.LearningAssistantException;
import com.chandler.learning.agent.identity.application.UserDisplayNameService;
import com.chandler.learning.agent.learning.application.ReviewSchedulePolicy;
import com.chandler.learning.agent.learning.application.LearningReviewService;
import com.chandler.learning.agent.learning.domain.entity.LearningReviewRecord;
import com.chandler.learning.agent.vocabulary.domain.entity.LearningWordProgress;
import com.chandler.learning.agent.vocabulary.domain.entity.VocabularyCatalogEntry;
import com.chandler.learning.agent.system.application.SystemLogService;
import com.chandler.learning.agent.vocabulary.infrastructure.mapper.LearningWordbookEntryMapper;
import com.chandler.learning.agent.vocabulary.infrastructure.mapper.LearningWordbookMapper;
import com.chandler.learning.agent.vocabulary.infrastructure.mapper.EnglishVocabularyStudyRecordMapper;
import com.chandler.learning.agent.vocabulary.application.EnglishVocabularyStudyService;
import com.chandler.learning.agent.ai.agent.domain.constant.AiScenarioConstants;
import com.chandler.learning.agent.common.constant.CommonConstants;
import com.chandler.learning.agent.common.exception.LearningErrorCode;
import com.chandler.learning.agent.identity.domain.constant.LearningActivityConstants;
import com.chandler.learning.agent.vocabulary.domain.constant.ReviewConstants;
import com.chandler.learning.agent.vocabulary.domain.constant.VocabularyCardConstants;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 单词本与复习计划服务。
 * <p>
 * 负责单词本管理、词条状态、学习笔记，以及基于艾宾浩斯间隔的复习排期。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WordbookService {

    private static final int WRITE_BATCH_SIZE = 200;

    private final LearningWordbookMapper wordbookMapper;
    private final LearningWordbookEntryMapper entryMapper;
    private final LearningReviewService reviewService;
    private final EnglishVocabularyStudyRecordMapper vocabularyMapper;
    private final EnglishVocabularyStudyService vocabularyStudyService;
    private final VocabularyInsightService vocabularyInsightService;
    private final SystemLogService systemLogService;
    private final UserDisplayNameService userDisplayNameService;
    private final ObjectMapper objectMapper;
    private final ReviewSchedulePolicy reviewSchedulePolicy;
    private final WordbookResponseAssembler responseAssembler;

    /** 按用户批量统计有效个人单词本数，供系统用户中心使用。 */
    public Map<Long, Integer> countByUserIds(java.util.Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        List<Map<String, Object>> rows = wordbookMapper.selectMaps(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<LearningWordbook>()
                .select("user_id AS userId", "COUNT(*) AS count")
                .in("user_id", userIds)
                .eq("deleted", false)
                .groupBy("user_id"));
        Map<Long, Integer> result = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            if (row.get("userId") instanceof Number userId && row.get("count") instanceof Number count) {
                result.put(userId.longValue(), count.intValue());
            }
        }
        return Map.copyOf(result);
    }

    /** 向其他业务域暴露用户单词本归属校验。 */
    public LearningWordbook requireOwnedWordbook(Long userId, Long wordbookId) {
        return requireWordbook(userId, wordbookId);
    }

    /** 按用户和单词本批量读取词条，并保持调用方给出的词条顺序由调用方负责。 */
    public List<LearningWordbookEntry> findOwnedEntries(Long userId, Long wordbookId,
                                                        java.util.Collection<Long> entryIds) {
        if (entryIds == null || entryIds.isEmpty()) {
            return List.of();
        }
        return entryMapper.selectList(new LambdaQueryWrapper<LearningWordbookEntry>()
                .eq(LearningWordbookEntry::getUserId, userId)
                .eq(LearningWordbookEntry::getWordbookId, wordbookId)
                .eq(LearningWordbookEntry::getDeleted, false)
                .in(LearningWordbookEntry::getId, entryIds));
    }

    /** 为场景学习创建或恢复个人单词本词条，并保存导入词的基础快照。 */
    public LearningWordbookEntry ensureLearningEntry(Long userId, Long wordbookId,
                                                      VocabularyCatalogEntry source,
                                                      LearningWordProgress progress,
                                                      String term, String normalizedTerm,
                                                      boolean cardRequired, LocalDateTime now) {
        return ensureLearningEntries(userId, wordbookId, List.of(new LearningEntryCommand(
                source, progress, term, normalizedTerm, cardRequired)), now).get(normalizedTerm);
    }

    /** 场景学习个人词条的批量写入命令。 */
    public record LearningEntryCommand(VocabularyCatalogEntry source, LearningWordProgress progress,
                                       String term, String normalizedTerm, boolean cardRequired) {}

    /**
     * 批量创建或恢复场景所需个人词条，再统一回读真实主键供学习单元关联。
     */
    public Map<String, LearningWordbookEntry> ensureLearningEntries(Long userId, Long wordbookId,
                                                                    Collection<LearningEntryCommand> commands,
                                                                    LocalDateTime now) {
        if (commands == null || commands.isEmpty()) {
            return Map.of();
        }
        Map<String, LearningEntryCommand> uniqueCommands = commands.stream()
                .filter(command -> command != null && StringUtils.hasText(command.normalizedTerm())
                        && command.progress() != null)
                .collect(java.util.stream.Collectors.toMap(
                        LearningEntryCommand::normalizedTerm,
                        command -> command,
                        (left, right) -> left.cardRequired() ? left : right,
                        LinkedHashMap::new));
        List<LearningWordbookEntry> upserts = new ArrayList<>(uniqueCommands.size());
        for (LearningEntryCommand command : uniqueCommands.values()) {
            VocabularyCatalogEntry source = command.source();
            LearningWordbookEntry entry = LearningWordbookEntry.createImported(
                    userId, wordbookId, command.progress().getId(), source == null ? null : source.getId(),
                    source == null ? command.term() : source.effectiveTerm(), command.normalizedTerm(),
                    responseAssembler.basicSnapshot(source, command.term()), now);
            if (command.cardRequired()) {
                entry.setCardStatus(VocabularyCardConstants.STATUS_MISSING);
            }
            upserts.add(entry);
        }
        for (int start = 0; start < upserts.size(); start += WRITE_BATCH_SIZE) {
            entryMapper.upsertLearningBatch(upserts.subList(start, Math.min(start + WRITE_BATCH_SIZE, upserts.size())));
        }
        List<String> normalizedTerms = new ArrayList<>(uniqueCommands.keySet());
        Map<String, LearningWordbookEntry> result = new LinkedHashMap<>();
        entryMapper.selectByNormalizedTermsIncludingDeleted(wordbookId, normalizedTerms).forEach(entry ->
                result.put(entry.getNormalizedTerm(), entry));
        return Map.copyOf(result);
    }

    /**
     * 创建或保存 {@code ensureDefaultWordbook} 相关业务。
     */
    public LearningWordbook ensureDefaultWordbook(Long userId) {
        LearningWordbook existing = wordbookMapper.selectOne(new LambdaQueryWrapper<LearningWordbook>()
                .eq(LearningWordbook::getUserId, userId)
                .eq(LearningWordbook::getIsDefault, true)
                .eq(LearningWordbook::getDeleted, false)
                .last(CommonConstants.SQL_LIMIT_ONE));
        if (existing != null) {
            return existing;
        }

        LocalDateTime now = LocalDateTime.now();
        LearningWordbook wordbook = LearningWordbook.createDefault(userId, now);
        wordbookMapper.insert(wordbook);
        systemLogService.record(userId, SystemLogType.WORDBOOK, "创建默认单词本", wordbook.getName());
        log.info("用户「{}」创建了默认单词本「{}」", userDisplayNameService.userName(userId), wordbook.getName());
        return wordbook;
    }

    /**
     * 查询 {@code listWordbooks} 相关业务。
     */
    public List<WordbookResponse> listWordbooks(Long userId) {
        ensureDefaultWordbook(userId);
        return wordbookMapper.selectWordbookSummaries(userId);
    }

    /**
     * 创建或保存 {@code createWordbook} 相关业务。
     */
    public WordbookResponse createWordbook(Long userId, WordbookSaveRequest request) {
        LocalDateTime now = LocalDateTime.now();
        if (Boolean.TRUE.equals(request.getIsDefault())) {
            clearDefault(userId);
        }

        LearningWordbook wordbook = LearningWordbook.create(userId, request.getName().trim(),
                trimToNull(request.getDescription()), Boolean.TRUE.equals(request.getIsDefault()), now);
        wordbookMapper.insert(wordbook);
        systemLogService.record(userId, SystemLogType.WORDBOOK, "创建单词本", wordbook.getName());
        log.info("用户「{}」创建了单词本「{}」，是否设为默认：{}",
                userDisplayNameService.userName(userId),
                wordbook.getName(),
                wordbook.getIsDefault());
        return responseAssembler.toWordbookResponse(wordbook);
    }

    /**
     * 更新 {@code updateWordbook} 相关业务。
     */
    public WordbookResponse updateWordbook(Long userId, Long wordbookId, WordbookSaveRequest request) {
        LearningWordbook wordbook = requireWordbook(userId, wordbookId);
        if (Boolean.TRUE.equals(request.getIsDefault())) {
            clearDefault(userId);
        }
        wordbook.updateProfile(request.getName().trim(), trimToNull(request.getDescription()),
                Boolean.TRUE.equals(request.getIsDefault()), LocalDateTime.now());
        wordbookMapper.updateById(wordbook);
        systemLogService.record(userId, SystemLogType.WORDBOOK, "更新单词本", wordbook.getName());
        log.info("用户「{}」更新了单词本「{}」，是否设为默认：{}",
                userDisplayNameService.userName(userId),
                wordbook.getName(),
                wordbook.getIsDefault());
        return responseAssembler.toWordbookResponse(wordbook);
    }

    /**
     * 更新 {@code deleteWordbook} 相关业务。
     */
    public void deleteWordbook(Long userId, Long wordbookId) {
        LearningWordbook wordbook = requireWordbook(userId, wordbookId);
        long entryCount = entryMapper.selectCount(new LambdaQueryWrapper<LearningWordbookEntry>()
                .eq(LearningWordbookEntry::getUserId, userId)
                .eq(LearningWordbookEntry::getWordbookId, wordbookId)
                .eq(LearningWordbookEntry::getDeleted, false));
        if (entryCount > 0) {
            throw LearningAssistantException.badRequest(
                    LearningErrorCode.WORDBOOK_NOT_EMPTY,
                    "单词本中还有单词，不能删除");
        }
        LocalDateTime now = LocalDateTime.now();
        wordbook.markDeleted(now);
        wordbookMapper.updateById(wordbook);

        LearningWordbookEntry updateEntry = new LearningWordbookEntry();
        updateEntry.setDeleted(true);
        updateEntry.setUpdateTime(now);
        entryMapper.update(updateEntry, new LambdaUpdateWrapper<LearningWordbookEntry>()
                .eq(LearningWordbookEntry::getUserId, userId)
                .eq(LearningWordbookEntry::getWordbookId, wordbookId)
                .eq(LearningWordbookEntry::getDeleted, false));

        LearningWordbook nextDefault = wordbookMapper.selectOne(new LambdaQueryWrapper<LearningWordbook>()
                .eq(LearningWordbook::getUserId, userId)
                .eq(LearningWordbook::getDeleted, false)
                .orderByAsc(LearningWordbook::getCreateTime)
                .last(CommonConstants.SQL_LIMIT_ONE));
        if (nextDefault != null) {
            nextDefault.changeDefault(true, now);
            wordbookMapper.updateById(nextDefault);
        }
        systemLogService.record(userId, SystemLogType.WORDBOOK, "删除单词本", wordbook.getName());
        log.info("用户「{}」删除了单词本「{}」", userDisplayNameService.userName(userId), wordbook.getName());
        log.debug("单词本删除后重新选择默认单词本 userId={} deletedWordbookId={} nextDefaultId={}",
                userId, wordbookId, nextDefault == null ? null : nextDefault.getId());
    }

    /**
     * 处理 {@code transferEntry} 相关业务。
     */
    @Transactional(rollbackFor = Exception.class)
    public WordbookEntryResponse transferEntry(Long userId, Long entryId, WordbookEntryTransferRequest request) {
        LearningWordbookEntry source = requireEntry(userId, entryId);
        LearningWordbook targetWordbook = requireWordbook(userId, request.getTargetWordbookId());
        if (Objects.equals(source.getWordbookId(), targetWordbook.getId())) {
            return responseAssembler.toEntryResponse(source);
        }
        boolean copy = Boolean.TRUE.equals(request.getCopy());
        LocalDateTime now = LocalDateTime.now();
        if (copy) {
            LearningWordbookEntry clone = source.copyTo(targetWordbook.getId(), now);
            entryMapper.insert(clone);
            systemLogService.record(userId, SystemLogType.WORDBOOK, "复制词条",
                    source.getNormalizedTerm() + " -> " + targetWordbook.getName());
            log.info("用户「{}」把单词「{}」复制到了单词本「{}」",
                    userDisplayNameService.userName(userId),
                    source.getNormalizedTerm(),
                    targetWordbook.getName());
            return responseAssembler.toEntryResponse(clone);
        }
        source.moveTo(targetWordbook.getId(), now);
        entryMapper.updateById(source);
        systemLogService.record(userId, SystemLogType.WORDBOOK, "移动词条",
                source.getNormalizedTerm() + " -> " + targetWordbook.getName());
        log.info("用户「{}」把单词「{}」移动到了单词本「{}」",
                userDisplayNameService.userName(userId),
                source.getNormalizedTerm(),
                targetWordbook.getName());
        return responseAssembler.toEntryResponse(source);
    }

    /**
     * 处理 {@code activity} 相关业务。
     */
    public LearningActivityResponse activity(Long userId, int days) {
        int resolvedDays = Math.max(LearningActivityConstants.MIN_DAYS, Math.min(days, LearningActivityConstants.MAX_DAYS));
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(resolvedDays - 1L);
        LocalDateTime startTime = startDate.atStartOfDay();

        Map<LocalDate, LearningActivityDayResponse> dayMap = new LinkedHashMap<>();
        for (int index = 0; index < resolvedDays; index++) {
            LocalDate date = startDate.plusDays(index);
            LearningActivityDayResponse item = new LearningActivityDayResponse();
            item.setDate(date.toString());
            item.setLearnedCount(0);
            item.setReviewCount(0);
            item.setTotalCount(0);
            dayMap.put(date, item);
        }

        List<LearningWordbookEntry> learned = entryMapper.selectList(new LambdaQueryWrapper<LearningWordbookEntry>()
                .eq(LearningWordbookEntry::getUserId, userId)
                .ge(LearningWordbookEntry::getCreateTime, startTime));
        for (LearningWordbookEntry entry : learned) {
            LocalDate date = entry.getCreateTime() == null ? null : entry.getCreateTime().toLocalDate();
            LearningActivityDayResponse item = dayMap.get(date);
            if (item != null) {
                item.setLearnedCount(nullToZero(item.getLearnedCount()) + CommonConstants.SEQUENCE_STEP);
            }
        }

        List<LearningReviewRecord> reviews = reviewService.listSince(userId, startTime);
        for (LearningReviewRecord review : reviews) {
            LocalDate date = review.getCreateTime() == null ? null : review.getCreateTime().toLocalDate();
            LearningActivityDayResponse item = dayMap.get(date);
            if (item != null) {
                item.setReviewCount(nullToZero(item.getReviewCount()) + CommonConstants.SEQUENCE_STEP);
            }
        }

        int learnedTotal = 0;
        int reviewTotal = 0;
        for (LearningActivityDayResponse item : dayMap.values()) {
            int learnedCount = nullToZero(item.getLearnedCount());
            int reviewCount = nullToZero(item.getReviewCount());
            item.setTotalCount(learnedCount + reviewCount);
            learnedTotal += learnedCount;
            reviewTotal += reviewCount;
        }

        LearningActivityResponse response = new LearningActivityResponse();
        response.setDays(resolvedDays);
        response.setLearnedTotal(learnedTotal);
        response.setReviewTotal(reviewTotal);
        response.setItems(List.copyOf(dayMap.values()));
        return response;
    }

    /**
     * 创建或保存 {@code addEntry} 相关业务。
     */
    public WordbookEntryResponse addEntry(Long userId, Long wordbookId, AddWordbookEntryRequest request) {
        LearningWordbook wordbook = requireWordbook(userId, wordbookId);
        String normalizedTerm = normalize(request.getTerm());
        if (!StringUtils.hasText(normalizedTerm)) {
            throw LearningAssistantException.badRequest(
                    LearningErrorCode.VOCABULARY_EMPTY,
                    "单词不能为空");
        }

        LearningWordbookEntry existing = entryMapper.selectIncludingDeleted(wordbook.getId(), normalizedTerm);
        if (existing != null) {
            LocalDateTime now = LocalDateTime.now();
            EnglishVocabularyStudyRecord vocabulary = findVocabulary(normalizedTerm);
            if (Boolean.TRUE.equals(existing.getDeleted())) {
                existing.restore(trimToNull(request.getNote()), now);
                if (vocabulary != null) {
                    responseAssembler.applyVocabularySnapshot(existing, vocabulary, now);
                }
                entryMapper.restoreDeletedById(existing.getId());
                entryMapper.updateById(existing);
                systemLogService.record(userId, SystemLogType.WORDBOOK, "恢复词条", existing.getNormalizedTerm());
                log.info("用户「{}」把单词「{}」重新加入到单词本「{}」中",
                        userDisplayNameService.userName(userId),
                        existing.getNormalizedTerm(),
                        wordbook.getName());
            } else if (responseAssembler.refreshSnapshotIfVocabularyChanged(existing, vocabulary, now)) {
                entryMapper.updateById(existing);
                systemLogService.record(userId, SystemLogType.WORDBOOK, "刷新词条学习卡", existing.getNormalizedTerm());
                log.info("用户「{}」把单词「{}」在单词本「{}」中的学习卡更新为最新 AI 结果",
                        userDisplayNameService.userName(userId),
                        existing.getNormalizedTerm(),
                        wordbook.getName());
            }
            log.debug("单词本中已存在单词 userId={} wordbookId={} term={}", userId, wordbook.getId(), normalizedTerm);
            return responseAssembler.toEntryResponse(existing);
        }

        EnglishVocabularyStudyRecord vocabulary = findVocabulary(normalizedTerm);
        if (vocabulary == null) {
            VocabularyStudyRequest studyRequest = new VocabularyStudyRequest();
            studyRequest.setTerm(request.getTerm());
            vocabularyStudyService.study(studyRequest);
            vocabulary = findVocabulary(normalizedTerm);
        }
        if (vocabulary == null) {
            throw LearningAssistantException.notFound(
                    LearningErrorCode.VOCABULARY_RECORD_NOT_FOUND,
                    "词汇学习记录不存在: " + normalizedTerm);
        }

        LocalDateTime now = LocalDateTime.now();
        LearningWordbookEntry entry = LearningWordbookEntry.createNew(userId, wordbook.getId(),
                vocabulary, trimToNull(request.getNote()), now);
        responseAssembler.applyVocabularySnapshot(entry, vocabulary, now);
        entryMapper.insert(entry);
        systemLogService.record(userId, SystemLogType.WORDBOOK, "加入单词本", entry.getNormalizedTerm());
        log.info("用户「{}」把单词「{}」添加到单词本「{}」中",
                userDisplayNameService.userName(userId),
                entry.getNormalizedTerm(),
                wordbook.getName());
        return responseAssembler.toEntryResponse(entry);
    }

    /** 分页读取单词本轻量词条，词卡正文由详情接口按需加载。 */
    public WordbookEntryPageResponse pageEntries(Long userId, Long wordbookId, boolean dueOnly, String status,
                                                 String keyword, Integer page, Integer pageSize) {
        requireWordbook(userId, wordbookId);
        int current = page == null || page < 1 ? 1 : page;
        int size = pageSize == null || pageSize < 1 ? 30 : Math.min(pageSize, 100);
        String trimmedKeyword = StringUtils.hasText(keyword) ? keyword.trim() : null;
        Page<LearningWordbookEntry> result = new Page<>(current, size);
        LambdaQueryWrapper<LearningWordbookEntry> wrapper = new LambdaQueryWrapper<LearningWordbookEntry>()
                .select(LearningWordbookEntry::getId,
                        LearningWordbookEntry::getWordbookId,
                        LearningWordbookEntry::getTerm,
                        LearningWordbookEntry::getNormalizedTerm,
                        LearningWordbookEntry::getStatus,
                        LearningWordbookEntry::getReviewStage,
                        LearningWordbookEntry::getMasteryScore,
                        LearningWordbookEntry::getLastReviewTime,
                        LearningWordbookEntry::getNextReviewTime,
                        LearningWordbookEntry::getReviewCount,
                        LearningWordbookEntry::getCorrectCount,
                        LearningWordbookEntry::getWrongCount,
                        LearningWordbookEntry::getCardStatus,
                        LearningWordbookEntry::getCreateTime)
                .eq(LearningWordbookEntry::getUserId, userId)
                .eq(LearningWordbookEntry::getWordbookId, wordbookId)
                .eq(LearningWordbookEntry::getDeleted, false)
                .eq(StringUtils.hasText(status), LearningWordbookEntry::getStatus, normalizeStatus(status))
                .and(trimmedKeyword != null, q -> q.likeRight(LearningWordbookEntry::getTerm, trimmedKeyword)
                        .or().likeRight(LearningWordbookEntry::getNormalizedTerm, trimmedKeyword.toLowerCase(Locale.ROOT)))
                .le(dueOnly, LearningWordbookEntry::getNextReviewTime, LocalDateTime.now())
                .orderByAsc(LearningWordbookEntry::getNextReviewTime)
                .orderByDesc(LearningWordbookEntry::getCreateTime);
        entryMapper.selectPage(result, wrapper);
        WordbookEntryPageResponse response = new WordbookEntryPageResponse();
        response.setItems(result.getRecords().stream().map(responseAssembler::toSummaryResponse).toList());
        response.setTotal(result.getTotal());
        response.setPage(current);
        response.setPageSize(size);
        return response;
    }

    /** 按需读取单个词条的完整词卡和个人学习快照。 */
    public WordbookEntryResponse detailEntry(Long userId, Long entryId) {
        return responseAssembler.toEntryResponse(requireEntry(userId, entryId));
    }

    /**
     * 查询 {@code listDueEntries} 相关业务。
     */
    public List<WordbookEntrySummaryResponse> listDueEntries(Long userId, Long wordbookId, Integer limit) {
        Long resolvedWordbookId = wordbookId == null ? ensureDefaultWordbook(userId).getId() : wordbookId;
        int resolvedLimit = Math.max(ReviewConstants.DUE_MIN_LIMIT,
                Math.min(limit == null ? ReviewConstants.DUE_DEFAULT_LIMIT : limit,
                        ReviewConstants.DUE_MAX_LIMIT));
        LocalDateTime now = LocalDateTime.now();
        List<LearningWordbookEntry> entries = entryMapper.selectList(new LambdaQueryWrapper<LearningWordbookEntry>()
                .select(LearningWordbookEntry::getId,
                        LearningWordbookEntry::getWordbookId,
                        LearningWordbookEntry::getTerm,
                        LearningWordbookEntry::getNormalizedTerm,
                        LearningWordbookEntry::getStatus,
                        LearningWordbookEntry::getReviewStage,
                        LearningWordbookEntry::getMasteryScore,
                        LearningWordbookEntry::getLastReviewTime,
                        LearningWordbookEntry::getNextReviewTime,
                        LearningWordbookEntry::getReviewCount,
                        LearningWordbookEntry::getCorrectCount,
                        LearningWordbookEntry::getWrongCount,
                        LearningWordbookEntry::getCardStatus,
                        LearningWordbookEntry::getCreateTime)
                .eq(LearningWordbookEntry::getUserId, userId)
                .eq(LearningWordbookEntry::getWordbookId, resolvedWordbookId)
                .eq(LearningWordbookEntry::getDeleted, false)
                .le(LearningWordbookEntry::getNextReviewTime, now)
                .orderByAsc(LearningWordbookEntry::getNextReviewTime)
                .orderByDesc(LearningWordbookEntry::getCreateTime)
                .last("LIMIT " + resolvedLimit));
        if (!entries.isEmpty()) {
            List<Long> entryIds = entries.stream().map(LearningWordbookEntry::getId).toList();
            entryMapper.update(null, new LambdaUpdateWrapper<LearningWordbookEntry>()
                    .in(LearningWordbookEntry::getId, entryIds)
                    .eq(LearningWordbookEntry::getUserId, userId)
                    .eq(LearningWordbookEntry::getWordbookId, resolvedWordbookId)
                    .eq(LearningWordbookEntry::getDeleted, false)
                    .le(LearningWordbookEntry::getNextReviewTime, now)
                    .setSql("due_count = COALESCE(due_count, 0) + 1")
                    .set(LearningWordbookEntry::getUpdateTime, now));
            entries.forEach(entry -> entry.markDue(now));
        }
        log.debug("待复习词条已查询 userId={} wordbookId={} count={}",
                userId,
                resolvedWordbookId,
                entries.size());
        return entries.stream().map(responseAssembler::toSummaryResponse).toList();
    }

    /**
     * 在没有到期任务时，为用户从当前单词本中重新挑选一组词条作为本轮复习队列。
     * <p>
     * 该动作只返回任务列表，不修改正式复习排期；只有提交复习结果时才更新下一次复习时间。
     */
    public List<WordbookEntrySummaryResponse> listRestartReviewEntries(Long userId, Long wordbookId, Integer limit) {
        LearningWordbook wordbook = wordbookId == null ? ensureDefaultWordbook(userId) : requireWordbook(userId, wordbookId);
        int resolvedLimit = Math.max(ReviewConstants.RESTART_MIN_LIMIT,
                Math.min(limit == null ? ReviewConstants.RESTART_DEFAULT_LIMIT : limit,
                        ReviewConstants.RESTART_MAX_LIMIT));
        List<LearningWordbookEntry> entries = entryMapper.selectList(new LambdaQueryWrapper<LearningWordbookEntry>()
                .select(LearningWordbookEntry::getId,
                        LearningWordbookEntry::getWordbookId,
                        LearningWordbookEntry::getTerm,
                        LearningWordbookEntry::getNormalizedTerm,
                        LearningWordbookEntry::getStatus,
                        LearningWordbookEntry::getReviewStage,
                        LearningWordbookEntry::getMasteryScore,
                        LearningWordbookEntry::getLastReviewTime,
                        LearningWordbookEntry::getNextReviewTime,
                        LearningWordbookEntry::getReviewCount,
                        LearningWordbookEntry::getCorrectCount,
                        LearningWordbookEntry::getWrongCount,
                        LearningWordbookEntry::getCardStatus,
                        LearningWordbookEntry::getCreateTime)
                .eq(LearningWordbookEntry::getUserId, userId)
                .eq(LearningWordbookEntry::getWordbookId, wordbook.getId())
                .eq(LearningWordbookEntry::getDeleted, false)
                .orderByAsc(LearningWordbookEntry::getLastReviewTime)
                .orderByAsc(LearningWordbookEntry::getMasteryScore)
                .orderByAsc(LearningWordbookEntry::getNextReviewTime)
                .orderByDesc(LearningWordbookEntry::getCreateTime)
                .last("LIMIT " + resolvedLimit));
        systemLogService.record(userId, SystemLogType.REVIEW, "重新生成复习任务",
                wordbook.getName() + "，共 " + entries.size() + " 个单词");
        log.info("用户「{}」重新生成了单词本「{}」的复习任务，共 {} 个单词",
                userDisplayNameService.userName(userId),
                wordbook.getName(),
                entries.size());
        log.debug("复习任务已重新生成 userId={} wordbookId={} limit={} count={}",
                userId,
                wordbook.getId(),
                resolvedLimit,
                entries.size());
        return entries.stream().map(responseAssembler::toSummaryResponse).toList();
    }

    /**
     * 更新 {@code updateEntry} 相关业务。
     */
    public WordbookEntryResponse updateEntry(Long userId, Long entryId, WordbookEntryUpdateRequest request) {
        LearningWordbookEntry entry = requireEntry(userId, entryId);
        if (request.getNote() != null) {
            entry.setNote(trimToNull(request.getNote()));
        }
        if (request.getStatus() != null) {
            entry.setStatus(normalizeStatus(request.getStatus()));
        }
        entry.setUpdateTime(LocalDateTime.now());
        entryMapper.updateById(entry);
        systemLogService.record(userId, SystemLogType.WORDBOOK, "更新词条", entry.getNormalizedTerm());
        log.info("用户「{}」更新了单词「{}」，当前熟练程度为「{}」，是否修改笔记：{}",
                userDisplayNameService.userName(userId),
                entry.getNormalizedTerm(),
                statusLabel(entry.getStatus()),
                request.getNote() != null);
        return responseAssembler.toEntryResponse(entry);
    }

    /**
     * 更新 {@code deleteEntry} 相关业务。
     */
    public void deleteEntry(Long userId, Long entryId) {
        LearningWordbookEntry entry = requireEntry(userId, entryId);
        entry.markDeleted(LocalDateTime.now());
        entryMapper.updateById(entry);
        systemLogService.record(userId, SystemLogType.WORDBOOK, "删除词条", entry.getNormalizedTerm());
        log.info("用户「{}」从单词本中删除了单词「{}」",
                userDisplayNameService.userName(userId),
                entry.getNormalizedTerm());
    }

    /**
     * 保存一次复习结果，并根据记忆状态计算下一次复习时间。
     */
    public ReviewSubmitResponse submitReview(Long userId, Long entryId, ReviewSubmitRequest request) {
        LearningWordbookEntry entry = entryMapper.selectById(entryId);
        if (entry == null || Boolean.TRUE.equals(entry.getDeleted()) || !entry.getUserId().equals(userId)) {
            throw LearningAssistantException.notFound(
                    LearningErrorCode.ENTRY_NOT_FOUND,
                    "单词本词条不存在: " + entryId);
        }
        ReviewResult result = ReviewResult.of(request.getResult());
        LocalDateTime now = LocalDateTime.now();

        ReviewResult.ReviewOutcome outcome = result.apply(entry);
        LocalDateTime nextReviewTime = reviewSchedulePolicy.nextReviewTime(
                now, outcome.stageAfter(), result.remembered(), result.vague());
        entry.completeReview(now, nextReviewTime, outcome.stageAfter(), outcome.masteryAfter(), now);
        entryMapper.updateById(entry);

        LearningReviewRecord record = new LearningReviewRecord();
        record.setUserId(userId);
        record.setWordbookId(entry.getWordbookId());
        record.setEntryId(entry.getId());
        record.setVocabularyId(entry.getVocabularyId());
        record.setWordProgressId(request.getWordProgressId() == null ? entry.getProgressId() : request.getWordProgressId());
        record.setPlanId(request.getPlanId());
        record.setUnitId(request.getUnitId());
        record.setAssessmentType(request.getAssessmentType());
        record.setQuestionJson(request.getQuestionJson());
        record.setAnswerText(request.getAnswerText());
        record.setCorrectAnswer(request.getCorrectAnswer());
        record.setCheckResult(request.getCheckResult());
        record.setTypingAccuracy(request.getTypingAccuracy());
        record.setHintLevel(request.getHintLevel());
        record.setAttemptCount(request.getAttemptCount());
        record.setDurationMillis(request.getDurationMillis());
        record.setNormalizedTerm(entry.getNormalizedTerm());
        record.setResult(result.getCode());
        record.setScore(request.getScore());
        record.setReviewStageBefore(outcome.stageBefore());
        record.setReviewStageAfter(outcome.stageAfter());
        record.setMasteryBefore(outcome.masteryBefore());
        record.setMasteryAfter(outcome.masteryAfter());
        record.setNextReviewTime(nextReviewTime);
        record.setDurationSeconds(request.getDurationSeconds());
        record.setCreateTime(now);
        reviewService.record(record);

        ReviewSubmitResponse response = new ReviewSubmitResponse();
        response.setEntryId(entry.getId());
        response.setNormalizedTerm(entry.getNormalizedTerm());
        response.setReviewStage(outcome.stageAfter());
        response.setMasteryScore(outcome.masteryAfter());
        response.setNextReviewTime(nextReviewTime);
        systemLogService.record(userId, SystemLogType.REVIEW, "提交复习结果", entry.getNormalizedTerm() + " -> " + result.getCode());
        log.info("用户「{}」完成了单词「{}」的复习，结果是「{}」，熟练度从 {} 提升到 {}，下次复习时间为 {}",
                userDisplayNameService.userName(userId),
                entry.getNormalizedTerm(),
                result.getLabel(),
                outcome.masteryBefore(),
                outcome.masteryAfter(),
                nextReviewTime);
        log.debug("复习排期已更新 userId={} entryId={} result={} stage={}=>{} mastery={}=>{} nextReviewTime={}",
                userId, entryId, result.getCode(), outcome.stageBefore(), outcome.stageAfter(),
                outcome.masteryBefore(), outcome.masteryAfter(), nextReviewTime);
        return response;
    }

    /**
     * 将公共 AI 词卡冻结到导入词条的个人快照中，后续公共缓存刷新不会覆盖个人学习详情。
     */
    public void attachVocabularyCard(Long userId, Long entryId, EnglishVocabularyStudyRecord vocabulary) {
        attachVocabularyCards(userId, Map.of(entryId, vocabulary));
        log.debug("个人词条已写入 AI 词卡快照 userId={} entryId={} vocabularyId={}",
                userId, entryId, vocabulary.getId());
    }

    /**
     * 批量冻结个人词条的词卡快照。词卡、标签、关联关系和词条更新均按批次读取/写入，
     * 避免批量词卡任务在循环中逐词执行 SQL。
     */
    public void attachVocabularyCards(Long userId, Map<Long, EnglishVocabularyStudyRecord> vocabularyByEntryId) {
        if (vocabularyByEntryId == null || vocabularyByEntryId.isEmpty()) {
            return;
        }
        List<LearningWordbookEntry> entries = entryMapper.selectBatchIds(vocabularyByEntryId.keySet()).stream()
                .filter(entry -> userId.equals(entry.getUserId()))
                .toList();
        if (entries.isEmpty()) {
            return;
        }
        List<EnglishVocabularyStudyRecord> vocabularies = entries.stream()
                .map(entry -> vocabularyByEntryId.get(entry.getId()))
                .filter(java.util.Objects::nonNull)
                .toList();
        Map<Long, List<VocabularyTagResponse>> tagsByVocabularyId = vocabularyInsightService
                .listTagsByVocabularyIds(vocabularies.stream().map(EnglishVocabularyStudyRecord::getId).toList());
        Map<String, List<VocabularyRelationResponse>> relationsByTerm = vocabularyInsightService
                .listRelationsByNormalizedTerms(vocabularies.stream()
                        .map(EnglishVocabularyStudyRecord::getNormalizedTerm).toList());
        LocalDateTime now = LocalDateTime.now();
        List<LearningWordbookEntry> updates = new ArrayList<>();
        for (LearningWordbookEntry entry : entries) {
            EnglishVocabularyStudyRecord vocabulary = vocabularyByEntryId.get(entry.getId());
            if (vocabulary == null) {
                continue;
            }
            String tagsJson = writeJson(tagsByVocabularyId.getOrDefault(vocabulary.getId(), List.of()),
                    "单词本词条标签快照序列化失败");
            String relationsJson = writeJson(relationsByTerm.getOrDefault(vocabulary.getNormalizedTerm(), List.of()),
                    "单词本词条关联词快照序列化失败");
            responseAssembler.applyVocabularySnapshot(entry, vocabulary, now, tagsJson, relationsJson);
            entry.setVocabularyId(vocabulary.getId());
            entry.setTerm(vocabulary.getTerm());
            entry.setNormalizedTerm(vocabulary.getNormalizedTerm());
            entry.setUpdateTime(now);
            updates.add(entry);
        }
        for (int start = 0; start < updates.size(); start += WRITE_BATCH_SIZE) {
            entryMapper.updateVocabularyCardBatch(updates.subList(start, Math.min(start + WRITE_BATCH_SIZE, updates.size())));
        }
    }

    /**
     * 为单词本词条生成或刷新 AI 词卡。
     */
    public WordbookEntryResponse generateCard(Long userId, Long entryId, boolean forceRefresh) {
        LearningWordbookEntry entry = requireEntry(userId, entryId);
        VocabularyStudyRequest studyRequest = new VocabularyStudyRequest();
        studyRequest.setTerm(entry.getTerm());
        studyRequest.setAgentCode(AiScenarioConstants.VOCABULARY_AGENT_CODE);
        studyRequest.setTemplateCode(AiScenarioConstants.VOCABULARY_TEMPLATE_CODE);
        studyRequest.setForceRefresh(forceRefresh);
        VocabularyStudyResponse studyResponse = vocabularyStudyService.study(studyRequest);

        EnglishVocabularyStudyRecord vocabulary = vocabularyMapper.selectById(studyResponse.getId());
        if (vocabulary != null) {
            attachVocabularyCard(userId, entryId, vocabulary);
        }
        systemLogService.record(userId, SystemLogType.WORDBOOK,
                forceRefresh ? "重新生成词卡" : "生成词卡", entry.getNormalizedTerm());
        log.info("用户「{}」为单词本词条「{}」{} AI 词卡",
                userDisplayNameService.userName(userId), entry.getNormalizedTerm(),
                forceRefresh ? "重新生成" : "生成");
        return responseAssembler.toEntryResponse(requireEntry(userId, entryId));
    }

    /**
     * 处理 {@code requireWordbook} 相关业务。
     */
    private LearningWordbook requireWordbook(Long userId, Long wordbookId) {
        LearningWordbook wordbook = wordbookMapper.selectById(wordbookId);
        if (wordbook == null || Boolean.TRUE.equals(wordbook.getDeleted()) || !wordbook.getUserId().equals(userId)) {
            throw LearningAssistantException.notFound(
                    LearningErrorCode.WORDBOOK_NOT_FOUND,
                    "单词本不存在: " + wordbookId);
        }
        return wordbook;
    }

    private String writeJson(Object value, String errorMessage) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            log.warn("{} error={}", errorMessage, ex.getMessage());
            return null;
        }
    }

    /**
     * 处理 {@code requireEntry} 相关业务。
     */
    private LearningWordbookEntry requireEntry(Long userId, Long entryId) {
        LearningWordbookEntry entry = entryMapper.selectById(entryId);
        if (entry == null || Boolean.TRUE.equals(entry.getDeleted()) || !entry.getUserId().equals(userId)) {
            throw LearningAssistantException.notFound(
                    LearningErrorCode.ENTRY_NOT_FOUND,
                    "单词本词条不存在: " + entryId);
        }
        return entry;
    }

    /**
     * 查询 {@code findVocabulary} 相关业务。
     */
    private EnglishVocabularyStudyRecord findVocabulary(String normalizedTerm) {
        return vocabularyMapper.selectOne(new LambdaQueryWrapper<EnglishVocabularyStudyRecord>()
                .eq(EnglishVocabularyStudyRecord::getNormalizedTerm, normalizedTerm)
                .last(CommonConstants.SQL_LIMIT_ONE));
    }

    /**
     * 更新 {@code clearDefault} 相关业务。
     */
    private void clearDefault(Long userId) {
        wordbookMapper.update(null, new LambdaUpdateWrapper<LearningWordbook>()
                .eq(LearningWordbook::getUserId, userId)
                .eq(LearningWordbook::getIsDefault, true)
                .eq(LearningWordbook::getDeleted, false)
                .set(LearningWordbook::getIsDefault, false)
                .set(LearningWordbook::getUpdateTime, LocalDateTime.now()));
    }

    /**
     * 处理 {@code normalizeStatus} 相关业务。
     */
    private String normalizeStatus(String status) {
        return ReviewStatus.of(status).getCode();
    }

    /**
     * 处理 {@code inferStatus} 相关业务。
     */
    private String inferStatus(LearningWordbookEntry entry) {
        return ReviewStatus.infer(entry.getMasteryScore(), entry.getWrongCount(), entry.getCorrectCount()).getCode();
    }

    /**
     * 处理 {@code normalize} 相关业务。
     */
    private String normalize(String term) {
        return term == null ? "" : term.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    /**
     * 处理 {@code trimToNull} 相关业务。
     */
    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /**
     * 处理 {@code nullToZero} 相关业务。
     */
    private int nullToZero(Integer value) {
        return value == null ? 0 : value;
    }

    /**
     * 处理 {@code statusLabel} 相关业务。
     */
    private String statusLabel(String status) {
        return ReviewStatus.of(status).getLabel();
    }
}
