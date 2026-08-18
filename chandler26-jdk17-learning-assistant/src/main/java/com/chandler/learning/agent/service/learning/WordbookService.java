package com.chandler.learning.agent.service.learning;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.chandler.learning.agent.domain.dto.learning.AddWordbookEntryRequest;
import com.chandler.learning.agent.domain.dto.learning.LearningActivityDayResponse;
import com.chandler.learning.agent.domain.dto.learning.LearningActivityResponse;
import com.chandler.learning.agent.domain.dto.learning.ReviewSubmitRequest;
import com.chandler.learning.agent.domain.dto.learning.ReviewSubmitResponse;
import com.chandler.learning.agent.domain.dto.learning.VocabularyRelationResponse;
import com.chandler.learning.agent.domain.dto.learning.VocabularyTagResponse;
import com.chandler.learning.agent.domain.dto.learning.WordbookEntryTransferRequest;
import com.chandler.learning.agent.domain.dto.learning.WordbookEntryResponse;
import com.chandler.learning.agent.domain.dto.learning.WordbookEntryUpdateRequest;
import com.chandler.learning.agent.domain.dto.learning.WordbookResponse;
import com.chandler.learning.agent.domain.dto.learning.WordbookSaveRequest;
import com.chandler.learning.agent.domain.dto.vocabulary.VocabularyStudyRequest;
import com.chandler.learning.agent.domain.dto.vocabulary.VocabularyStudyResponse;
import com.chandler.learning.agent.domain.enums.ReviewResult;
import com.chandler.learning.agent.domain.enums.ReviewStatus;
import com.chandler.learning.agent.domain.enums.SystemLogType;
import com.chandler.learning.agent.domain.entity.learning.LearningReviewRecord;
import com.chandler.learning.agent.domain.entity.learning.LearningWordbook;
import com.chandler.learning.agent.domain.entity.learning.LearningWordbookEntry;
import com.chandler.learning.agent.domain.entity.vocabulary.EnglishVocabularyStudyRecord;
import com.chandler.learning.agent.exception.LearningAssistantException;
import com.chandler.learning.agent.mapper.learning.LearningReviewRecordMapper;
import com.chandler.learning.agent.mapper.learning.LearningWordbookEntryMapper;
import com.chandler.learning.agent.mapper.learning.LearningWordbookMapper;
import com.chandler.learning.agent.mapper.vocabulary.EnglishVocabularyStudyRecordMapper;
import com.chandler.learning.agent.service.vocabulary.EnglishVocabularyStudyService;
import com.chandler.learning.agent.support.LearningConstants;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
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

    private final LearningWordbookMapper wordbookMapper;
    private final LearningWordbookEntryMapper entryMapper;
    private final LearningReviewRecordMapper reviewRecordMapper;
    private final EnglishVocabularyStudyRecordMapper vocabularyMapper;
    private final EnglishVocabularyStudyService vocabularyStudyService;
    private final VocabularyInsightService vocabularyInsightService;
    private final SystemLogService systemLogService;
    private final UserDisplayNameService userDisplayNameService;
    private final ObjectMapper objectMapper;

    /**
     * 创建或保存 {@code ensureDefaultWordbook} 相关业务。
     */
    public LearningWordbook ensureDefaultWordbook(Long userId) {
        LearningWordbook existing = wordbookMapper.selectOne(new LambdaQueryWrapper<LearningWordbook>()
                .eq(LearningWordbook::getUserId, userId)
                .eq(LearningWordbook::getIsDefault, true)
                .eq(LearningWordbook::getDeleted, false)
                .last(LearningConstants.SQL_LIMIT_ONE));
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
        return wordbookMapper.selectList(new LambdaQueryWrapper<LearningWordbook>()
                        .eq(LearningWordbook::getUserId, userId)
                        .eq(LearningWordbook::getDeleted, false)
                        .orderByDesc(LearningWordbook::getIsDefault)
                        .orderByAsc(LearningWordbook::getCreateTime))
                .stream()
                .map(this::toWordbookResponse)
                .toList();
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
        return toWordbookResponse(wordbook);
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
        return toWordbookResponse(wordbook);
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
                    LearningConstants.ErrorCode.WORDBOOK_NOT_EMPTY,
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
                .last(LearningConstants.SQL_LIMIT_ONE));
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
            return toEntryResponse(source);
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
            return toEntryResponse(clone);
        }
        source.moveTo(targetWordbook.getId(), now);
        entryMapper.updateById(source);
        systemLogService.record(userId, SystemLogType.WORDBOOK, "移动词条",
                source.getNormalizedTerm() + " -> " + targetWordbook.getName());
        log.info("用户「{}」把单词「{}」移动到了单词本「{}」",
                userDisplayNameService.userName(userId),
                source.getNormalizedTerm(),
                targetWordbook.getName());
        return toEntryResponse(source);
    }

    /**
     * 处理 {@code activity} 相关业务。
     */
    public LearningActivityResponse activity(Long userId, int days) {
        int resolvedDays = Math.max(LearningConstants.Activity.MIN_DAYS, Math.min(days, LearningConstants.Activity.MAX_DAYS));
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
                item.setLearnedCount(nullToZero(item.getLearnedCount()) + LearningConstants.SEQUENCE_STEP);
            }
        }

        List<LearningReviewRecord> reviews = reviewRecordMapper.selectList(new LambdaQueryWrapper<LearningReviewRecord>()
                .eq(LearningReviewRecord::getUserId, userId)
                .ge(LearningReviewRecord::getCreateTime, startTime));
        for (LearningReviewRecord review : reviews) {
            LocalDate date = review.getCreateTime() == null ? null : review.getCreateTime().toLocalDate();
            LearningActivityDayResponse item = dayMap.get(date);
            if (item != null) {
                item.setReviewCount(nullToZero(item.getReviewCount()) + LearningConstants.SEQUENCE_STEP);
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
                    LearningConstants.ErrorCode.VOCABULARY_EMPTY,
                    "单词不能为空");
        }

        LearningWordbookEntry existing = entryMapper.selectIncludingDeleted(wordbook.getId(), normalizedTerm);
        if (existing != null) {
            LocalDateTime now = LocalDateTime.now();
            EnglishVocabularyStudyRecord vocabulary = findVocabulary(normalizedTerm);
            if (Boolean.TRUE.equals(existing.getDeleted())) {
                existing.restore(trimToNull(request.getNote()), now);
                if (vocabulary != null) {
                    applyVocabularySnapshot(existing, vocabulary, now);
                }
                entryMapper.restoreDeletedById(existing.getId());
                entryMapper.updateById(existing);
                systemLogService.record(userId, SystemLogType.WORDBOOK, "恢复词条", existing.getNormalizedTerm());
                log.info("用户「{}」把单词「{}」重新加入到单词本「{}」中",
                        userDisplayNameService.userName(userId),
                        existing.getNormalizedTerm(),
                        wordbook.getName());
            } else if (refreshSnapshotIfVocabularyChanged(existing, vocabulary, now)) {
                entryMapper.updateById(existing);
                systemLogService.record(userId, SystemLogType.WORDBOOK, "刷新词条学习卡", existing.getNormalizedTerm());
                log.info("用户「{}」把单词「{}」在单词本「{}」中的学习卡更新为最新 AI 结果",
                        userDisplayNameService.userName(userId),
                        existing.getNormalizedTerm(),
                        wordbook.getName());
            }
            log.debug("单词本中已存在单词 userId={} wordbookId={} term={}", userId, wordbook.getId(), normalizedTerm);
            return toEntryResponse(existing);
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
                    LearningConstants.ErrorCode.VOCABULARY_RECORD_NOT_FOUND,
                    "词汇学习记录不存在: " + normalizedTerm);
        }

        LocalDateTime now = LocalDateTime.now();
        LearningWordbookEntry entry = LearningWordbookEntry.createNew(userId, wordbook.getId(),
                vocabulary, trimToNull(request.getNote()), now);
        applyVocabularySnapshot(entry, vocabulary, now);
        entryMapper.insert(entry);
        systemLogService.record(userId, SystemLogType.WORDBOOK, "加入单词本", entry.getNormalizedTerm());
        log.info("用户「{}」把单词「{}」添加到单词本「{}」中",
                userDisplayNameService.userName(userId),
                entry.getNormalizedTerm(),
                wordbook.getName());
        return toEntryResponse(entry);
    }

    /**
     * 查询 {@code listEntries} 相关业务。
     */
    public List<WordbookEntryResponse> listEntries(Long userId, Long wordbookId, boolean dueOnly) {
        return listEntries(userId, wordbookId, dueOnly, null);
    }

    /**
     * 查询 {@code listEntries} 相关业务。
     */
    public List<WordbookEntryResponse> listEntries(Long userId, Long wordbookId, boolean dueOnly, String status) {
        requireWordbook(userId, wordbookId);
        LambdaQueryWrapper<LearningWordbookEntry> wrapper = new LambdaQueryWrapper<LearningWordbookEntry>()
                .eq(LearningWordbookEntry::getUserId, userId)
                .eq(LearningWordbookEntry::getWordbookId, wordbookId)
                .eq(LearningWordbookEntry::getDeleted, false)
                .eq(StringUtils.hasText(status), LearningWordbookEntry::getStatus, normalizeStatus(status))
                .le(dueOnly, LearningWordbookEntry::getNextReviewTime, LocalDateTime.now())
                .orderByAsc(LearningWordbookEntry::getNextReviewTime)
                .orderByDesc(LearningWordbookEntry::getCreateTime);
        return entryMapper.selectList(wrapper).stream()
                .map(this::toEntryResponse)
                .toList();
    }

    /**
     * 查询 {@code listDueEntries} 相关业务。
     */
    public List<WordbookEntryResponse> listDueEntries(Long userId, Long wordbookId) {
        Long resolvedWordbookId = wordbookId == null ? ensureDefaultWordbook(userId).getId() : wordbookId;
        List<LearningWordbookEntry> entries = entryMapper.selectList(new LambdaQueryWrapper<LearningWordbookEntry>()
                .eq(LearningWordbookEntry::getUserId, userId)
                .eq(LearningWordbookEntry::getWordbookId, resolvedWordbookId)
                .eq(LearningWordbookEntry::getDeleted, false)
                .le(LearningWordbookEntry::getNextReviewTime, LocalDateTime.now())
                .orderByAsc(LearningWordbookEntry::getNextReviewTime)
                .orderByDesc(LearningWordbookEntry::getCreateTime));
        for (LearningWordbookEntry entry : entries) {
            entry.markDue(LocalDateTime.now());
            entryMapper.updateById(entry);
        }
        log.debug("待复习词条已查询 userId={} wordbookId={} count={}",
                userId,
                resolvedWordbookId,
                entries.size());
        return entries.stream().map(this::toEntryResponse).toList();
    }

    /**
     * 在没有到期任务时，为用户从当前单词本中重新挑选一组词条作为本轮复习队列。
     * <p>
     * 该动作只返回任务列表，不修改正式复习排期；只有提交复习结果时才更新下一次复习时间。
     */
    public List<WordbookEntryResponse> listRestartReviewEntries(Long userId, Long wordbookId, Integer limit) {
        LearningWordbook wordbook = wordbookId == null ? ensureDefaultWordbook(userId) : requireWordbook(userId, wordbookId);
        int resolvedLimit = Math.max(LearningConstants.Review.RESTART_MIN_LIMIT,
                Math.min(limit == null ? LearningConstants.Review.RESTART_DEFAULT_LIMIT : limit,
                        LearningConstants.Review.RESTART_MAX_LIMIT));
        List<LearningWordbookEntry> entries = entryMapper.selectList(new LambdaQueryWrapper<LearningWordbookEntry>()
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
        return entries.stream().map(this::toEntryResponse).toList();
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
        return toEntryResponse(entry);
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
                    LearningConstants.ErrorCode.ENTRY_NOT_FOUND,
                    "单词本词条不存在: " + entryId);
        }
        ReviewResult result = ReviewResult.of(request.getResult());
        LocalDateTime now = LocalDateTime.now();

        ReviewResult.ReviewOutcome outcome = result.apply(entry);
        LocalDateTime nextReviewTime = nextReviewTime(now, outcome.stageAfter(), result.remembered(), result.vague());
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
        reviewRecordMapper.insert(record);

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
        LearningWordbookEntry entry = requireEntry(userId, entryId);
        applyVocabularySnapshot(entry, vocabulary, LocalDateTime.now());
        entry.setVocabularyId(vocabulary.getId());
        entry.setTerm(vocabulary.getTerm());
        entry.setNormalizedTerm(vocabulary.getNormalizedTerm());
        entry.setUpdateTime(LocalDateTime.now());
        entryMapper.updateById(entry);
        log.debug("个人词条已写入 AI 词卡快照 userId={} entryId={} vocabularyId={}",
                userId, entryId, vocabulary.getId());
    }

    /**
     * 为单词本词条生成或刷新 AI 词卡。
     */
    @Transactional(rollbackFor = Exception.class)
    public WordbookEntryResponse generateCard(Long userId, Long entryId, boolean forceRefresh) {
        LearningWordbookEntry entry = requireEntry(userId, entryId);
        VocabularyStudyRequest studyRequest = new VocabularyStudyRequest();
        studyRequest.setTerm(entry.getTerm());
        studyRequest.setAgentCode(LearningConstants.VOCABULARY_AGENT_CODE);
        studyRequest.setTemplateCode(LearningConstants.VOCABULARY_TEMPLATE_CODE);
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
        return toEntryResponse(requireEntry(userId, entryId));
    }

    /**
     * 转换 {@code toWordbookResponse} 相关业务。
     */
    private WordbookResponse toWordbookResponse(LearningWordbook wordbook) {
        WordbookResponse response = new WordbookResponse();
        response.setId(wordbook.getId());
        response.setName(wordbook.getName());
        response.setDescription(wordbook.getDescription());
        response.setIsDefault(wordbook.getIsDefault());
        response.setEntryCount(entryMapper.selectCount(new LambdaQueryWrapper<LearningWordbookEntry>()
                .eq(LearningWordbookEntry::getWordbookId, wordbook.getId())
                .eq(LearningWordbookEntry::getDeleted, false)));
        response.setDueCount(entryMapper.selectCount(new LambdaQueryWrapper<LearningWordbookEntry>()
                .eq(LearningWordbookEntry::getWordbookId, wordbook.getId())
                .eq(LearningWordbookEntry::getDeleted, false)
                .le(LearningWordbookEntry::getNextReviewTime, LocalDateTime.now())));
        response.setCreateTime(wordbook.getCreateTime());
        return response;
    }

    /**
     * 转换 {@code toEntryResponse} 相关业务。
     */
    private WordbookEntryResponse toEntryResponse(LearningWordbookEntry entry) {
        WordbookEntryResponse response = new WordbookEntryResponse();
        response.setId(entry.getId());
        response.setWordbookId(entry.getWordbookId());
        response.setVocabularyId(entry.getVocabularyId());
        response.setProgressId(entry.getProgressId());
        response.setCatalogEntryId(entry.getCatalogEntryId());
        response.setTerm(entry.getTerm());
        response.setNormalizedTerm(entry.getNormalizedTerm());
        response.setNote(entry.getNote());
        response.setStatus(StringUtils.hasText(entry.getStatus()) ? entry.getStatus() : inferStatus(entry));
        response.setReviewStage(entry.getReviewStage());
        response.setMasteryScore(entry.getMasteryScore());
        response.setLastReviewTime(entry.getLastReviewTime());
        response.setNextReviewTime(entry.getNextReviewTime());
        response.setReviewCount(entry.getReviewCount());
        response.setCorrectCount(entry.getCorrectCount());
        response.setWrongCount(entry.getWrongCount());
        response.setCreateTime(entry.getCreateTime());
        response.setParsed(readEntryParsed(entry));
        response.setSnapshotProvider(entry.getSnapshotProvider());
        response.setSnapshotModelName(entry.getSnapshotModelName());
        response.setSnapshotSessionId(entry.getSnapshotSessionId());
        response.setSnapshotTime(entry.getSnapshotTime());
        response.setCardStatus(entry.getCardStatus());
        response.setCardErrorMessage(entry.getCardErrorMessage());
        response.setCardGeneratedTime(entry.getCardGeneratedTime());
        response.setTags(readEntryTags(entry));
        response.setRelations(readEntryRelations(entry));
        return response;
    }

    /**
     * 更新 {@code applyVocabularySnapshot} 相关业务。
     */
    private void applyVocabularySnapshot(LearningWordbookEntry entry, EnglishVocabularyStudyRecord vocabulary, LocalDateTime now) {
        String tagsJson = writeJson(vocabularyInsightService.listTags(vocabulary.getId()),
                "单词本词条标签快照序列化失败");
        String relationsJson = writeJson(vocabularyInsightService.listRelations(vocabulary.getNormalizedTerm()),
                "单词本词条关联词快照序列化失败");
        entry.applyVocabularySnapshot(vocabulary, now, tagsJson, relationsJson);
    }

    /**
     * 处理 {@code refreshSnapshotIfVocabularyChanged} 相关业务。
     */
    private boolean refreshSnapshotIfVocabularyChanged(LearningWordbookEntry entry,
                                                       EnglishVocabularyStudyRecord vocabulary,
                                                       LocalDateTime now) {
        if (vocabulary == null) {
            return false;
        }
        boolean snapshotMissing = !StringUtils.hasText(entry.getSnapshotParsedJson());
        boolean sessionChanged = vocabulary.getSessionId() != null && !vocabulary.getSessionId().equals(entry.getSnapshotSessionId());
        boolean vocabularyNewer = vocabulary.getUpdateTime() != null
                && (entry.getSnapshotTime() == null || vocabulary.getUpdateTime().isAfter(entry.getSnapshotTime()));
        if (!snapshotMissing && !sessionChanged && !vocabularyNewer) {
            return false;
        }
        applyVocabularySnapshot(entry, vocabulary, now);
        entry.refreshVocabularyIdentity(vocabulary, now);
        return true;
    }

    /**
     * 查询 {@code readEntryParsed} 相关业务。
     */
    private Object readEntryParsed(LearningWordbookEntry entry) {
        if (StringUtils.hasText(entry.getSnapshotParsedJson())) {
            Object parsed = readJson(entry.getSnapshotParsedJson(), Object.class, "单词本词条个人结构化 JSON 快照读取失败", entry);
            if (parsed != null) {
                return parsed;
            }
        }
        return readParsed(entry.getVocabularyId());
    }

    /**
     * 查询 {@code readEntryTags} 相关业务。
     */
    private List<VocabularyTagResponse> readEntryTags(LearningWordbookEntry entry) {
        if (StringUtils.hasText(entry.getSnapshotTagsJson())) {
            List<VocabularyTagResponse> tags = readJsonList(entry.getSnapshotTagsJson(), VocabularyTagResponse.class,
                    "单词本词条标签快照读取失败", entry);
            if (tags != null) {
                return tags;
            }
        }
        return vocabularyInsightService.listTags(entry.getVocabularyId());
    }

    /**
     * 查询 {@code readEntryRelations} 相关业务。
     */
    private List<VocabularyRelationResponse> readEntryRelations(LearningWordbookEntry entry) {
        if (StringUtils.hasText(entry.getSnapshotRelationsJson())) {
            List<VocabularyRelationResponse> relations = readJsonList(entry.getSnapshotRelationsJson(), VocabularyRelationResponse.class,
                    "单词本词条关联词快照读取失败", entry);
            if (relations != null) {
                return vocabularyInsightService.enrichRelationPhonetics(entry.getVocabularyId(), relations);
            }
        }
        return vocabularyInsightService.listRelations(entry.getNormalizedTerm());
    }

    /**
     * 处理 {@code writeJson} 相关业务。
     */
    private String writeJson(Object value, String errorMessage) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            log.warn("{} error={}", errorMessage, ex.getMessage());
            return null;
        }
    }

    /**
     * 查询 {@code readJson} 相关业务。
     */
    private <T> T readJson(String json, Class<T> valueType, String errorMessage, LearningWordbookEntry entry) {
        try {
            return objectMapper.readValue(json, valueType);
        } catch (Exception ex) {
            log.warn("{} entryId={} term={} error={}",
                    errorMessage,
                    entry.getId(),
                    entry.getNormalizedTerm(),
                    ex.getMessage());
            return null;
        }
    }

    /**
     * 查询 {@code readJsonList} 相关业务。
     */
    private <T> List<T> readJsonList(String json, Class<T> elementType, String errorMessage, LearningWordbookEntry entry) {
        try {
            return objectMapper.readValue(json, objectMapper.getTypeFactory().constructCollectionType(List.class, elementType));
        } catch (Exception ex) {
            log.warn("{} entryId={} term={} error={}",
                    errorMessage,
                    entry.getId(),
                    entry.getNormalizedTerm(),
                    ex.getMessage());
            return null;
        }
    }

    /**
     * 查询 {@code readParsed} 相关业务。
     */
    private Object readParsed(Long vocabularyId) {
        EnglishVocabularyStudyRecord record = vocabularyMapper.selectById(vocabularyId);
        if (record == null || !StringUtils.hasText(record.getParsedJson())) {
            return null;
        }
        try {
            return objectMapper.readValue(record.getParsedJson(), Object.class);
        } catch (Exception ex) {
            log.warn("单词本词条结构化 JSON 读取失败 vocabularyId={} term={} error={}",
                    vocabularyId,
                    record.getNormalizedTerm(),
                    ex.getMessage());
            return null;
        }
    }

    /**
     * 处理 {@code requireWordbook} 相关业务。
     */
    private LearningWordbook requireWordbook(Long userId, Long wordbookId) {
        LearningWordbook wordbook = wordbookMapper.selectById(wordbookId);
        if (wordbook == null || Boolean.TRUE.equals(wordbook.getDeleted()) || !wordbook.getUserId().equals(userId)) {
            throw LearningAssistantException.notFound(
                    LearningConstants.ErrorCode.WORDBOOK_NOT_FOUND,
                    "单词本不存在: " + wordbookId);
        }
        return wordbook;
    }

    /**
     * 处理 {@code requireEntry} 相关业务。
     */
    private LearningWordbookEntry requireEntry(Long userId, Long entryId) {
        LearningWordbookEntry entry = entryMapper.selectById(entryId);
        if (entry == null || Boolean.TRUE.equals(entry.getDeleted()) || !entry.getUserId().equals(userId)) {
            throw LearningAssistantException.notFound(
                    LearningConstants.ErrorCode.ENTRY_NOT_FOUND,
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
                .last(LearningConstants.SQL_LIMIT_ONE));
    }

    /**
     * 更新 {@code clearDefault} 相关业务。
     */
    private void clearDefault(Long userId) {
        List<LearningWordbook> defaults = wordbookMapper.selectList(new LambdaQueryWrapper<LearningWordbook>()
                .eq(LearningWordbook::getUserId, userId)
                .eq(LearningWordbook::getIsDefault, true)
                .eq(LearningWordbook::getDeleted, false));
        for (LearningWordbook item : defaults) {
            item.changeDefault(false, LocalDateTime.now());
            wordbookMapper.updateById(item);
        }
    }

    /**
     * 处理 {@code nextReviewTime} 相关业务。
     */
    private LocalDateTime nextReviewTime(LocalDateTime now, int stage, boolean remembered, boolean vague) {
        LocalDateTime baseTime = avoidSleepWindow(now);
        if (vague) {
            return avoidSleepWindow(baseTime.plusDays(LearningConstants.Review.VAGUE_REVIEW_DELAY_DAYS));
        }
        if (!remembered) {
            return addAwakeHours(baseTime, LearningConstants.Review.FORGOTTEN_REVIEW_DELAY_HOURS);
        }
        return avoidSleepWindow(baseTime.plusDays(LearningConstants.Review.INTERVAL_DAYS[
                Math.max(LearningConstants.Review.INITIAL_STAGE,
                        Math.min(stage, LearningConstants.Review.INTERVAL_DAYS.length - LearningConstants.SEQUENCE_STEP))]));
    }

    LocalDateTime avoidSleepWindow(LocalDateTime reviewTime) {
        int hour = reviewTime.getHour();
        if (hour >= LearningConstants.Review.SLEEP_START_HOUR && hour < LearningConstants.Review.SLEEP_END_HOUR) {
            return reviewTime.toLocalDate().atTime(LearningConstants.Review.SLEEP_END_HOUR, LearningConstants.ZERO);
        }
        return reviewTime;
    }

    LocalDateTime addAwakeHours(LocalDateTime startTime, long hours) {
        LocalDateTime current = avoidSleepWindow(startTime);
        long remainingMinutes = hours * ChronoUnit.HOURS.getDuration().toMinutes();
        while (remainingMinutes > LearningConstants.ZERO) {
            LocalDateTime sleepStart = current.toLocalDate().atTime(
                    LearningConstants.Review.DAY_END_HOUR - LearningConstants.SEQUENCE_STEP,
                    LearningConstants.ZERO).plusHours(LearningConstants.SEQUENCE_STEP);
            long awakeMinutesToday = ChronoUnit.MINUTES.between(current, sleepStart);
            if (remainingMinutes <= awakeMinutesToday) {
                return avoidSleepWindow(current.plusMinutes(remainingMinutes));
            }
            remainingMinutes -= Math.max(awakeMinutesToday, LearningConstants.ZERO);
            current = current.toLocalDate().plusDays(LearningConstants.SEQUENCE_STEP)
                    .atTime(LearningConstants.Review.SLEEP_END_HOUR, LearningConstants.ZERO);
        }
        return avoidSleepWindow(current);
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
