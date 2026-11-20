package com.chandler.learning.agent.vocabulary.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.chandler.learning.agent.learning.application.LearningActivityService;
import com.chandler.learning.agent.learning.domain.enums.LearningActivityEventType;
import com.chandler.learning.agent.system.application.SystemLogService;
import com.chandler.learning.agent.system.domain.enums.SystemLogType;
import com.chandler.learning.agent.vocabulary.api.request.RecentSearchReviewSubmitRequest;
import com.chandler.learning.agent.vocabulary.api.response.RecentSearchReviewItemResponse;
import com.chandler.learning.agent.vocabulary.api.response.RecentSearchReviewQueueResponse;
import com.chandler.learning.agent.vocabulary.api.response.ReviewSubmitResponse;
import com.chandler.learning.agent.vocabulary.domain.constant.ReviewConstants;
import com.chandler.learning.agent.vocabulary.domain.entity.EnglishVocabularyStudyRecord;
import com.chandler.learning.agent.vocabulary.domain.entity.LearningVocabularyLookupHistory;
import com.chandler.learning.agent.vocabulary.domain.entity.LearningWordbookEntry;
import com.chandler.learning.agent.learning.domain.enums.ReviewResult;
import com.chandler.learning.agent.vocabulary.infrastructure.mapper.EnglishVocabularyStudyRecordMapper;
import com.chandler.learning.agent.vocabulary.infrastructure.mapper.LearningVocabularyLookupHistoryMapper;
import com.chandler.learning.agent.vocabulary.infrastructure.mapper.LearningWordbookEntryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户个人查词足迹与“近期搜索词汇轻量复习”应用服务。
 * <p>
 * 负责记录卡片查词、快捷查词与手动加词三类足迹，并按「今天+昨天查词优先，少于 5 个自动兜底最近 20 个」生成低压力复习队列。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VocabularyLookupHistoryService {

    public static final String SOURCE_STUDY = "study";
    public static final String SOURCE_QUICK_LOOKUP = "quick_lookup";
    public static final String SOURCE_WORDBOOK_ADD = "wordbook_add";
    public static final String SOURCE_WORDBOOK_RECENT = "wordbook_recent";

    private static final int DEFAULT_RECENT_DAYS = 2;
    private static final int DEFAULT_MIN_COUNT = 5;
    private static final int DEFAULT_FALLBACK_LIMIT = 20;
    private static final int MAX_RECENT_WINDOW_LIMIT = 100;

    private final LearningVocabularyLookupHistoryMapper lookupHistoryMapper;
    private final EnglishVocabularyStudyRecordMapper studyRecordMapper;
    private final LearningWordbookEntryMapper wordbookEntryMapper;
    private final LearningActivityService activityService;
    private final SystemLogService systemLogService;

    /**
     * 安全记录用户个人查词或手动加词足迹；异常被隔离，绝不阻断核心查词与加词动作。
     */
    public void recordLookup(Long userId, Long vocabularyId, String term, String normalizedTerm, String source) {
        if (userId == null) {
            return;
        }
        String resolvedNormalized = normalizeTerm(StringUtils.hasText(normalizedTerm) ? normalizedTerm : term);
        if (!StringUtils.hasText(resolvedNormalized)) {
            return;
        }
        String resolvedTerm = StringUtils.hasText(term) ? term.trim() : resolvedNormalized;
        String resolvedSource = normalizeSource(source);
        LocalDateTime now = LocalDateTime.now();

        LearningVocabularyLookupHistory item = new LearningVocabularyLookupHistory();
        item.setId(IdWorker.getId());
        item.setCreateBy(userId);
        item.setUpdateBy(userId);
        item.setUserId(userId);
        item.setVocabularyId(vocabularyId);
        item.setTerm(resolvedTerm);
        item.setNormalizedTerm(resolvedNormalized);
        item.setLookupSource(resolvedSource);
        item.setLookupCount(1);
        item.setFirstLookupTime(now);
        item.setLastLookupTime(now);
        item.setCreateTime(now);
        item.setUpdateTime(now);
        item.setDeleted(false);
        item.setVersion(0);

        try {
            lookupHistoryMapper.upsertLookup(item);
        } catch (RuntimeException ex) {
            log.warn("event=vocabulary_lookup_history_record result=failed userId={} term={} error={}",
                    userId, resolvedNormalized, ex.getClass().getSimpleName());
            log.debug("记录查词足迹失败 userId={} term={}", userId, resolvedNormalized, ex);
        }
    }

    /**
     * 获取当前用户的近期查词轻量复习队列：
     * 优先返回今天+昨天搜索过的词；若不足 5 个，则自动兜底展示最近搜索的 20 个词（无查词足迹时平滑兜底最近更新的 20 个词条）。
     */
    public RecentSearchReviewQueueResponse listRecentSearchReviewQueue(
            Long userId, Integer days, Integer minCount, Integer fallbackLimit) {
        int resolvedDays = days == null ? DEFAULT_RECENT_DAYS : Math.max(1, Math.min(days, 30));
        int resolvedMinCount = minCount == null ? DEFAULT_MIN_COUNT : Math.max(1, Math.min(minCount, 50));
        int resolvedFallbackLimit = fallbackLimit == null ? DEFAULT_FALLBACK_LIMIT : Math.max(1, Math.min(fallbackLimit, 50));

        LocalDateTime windowStart = LocalDate.now().minusDays(resolvedDays - 1L).atStartOfDay();
        List<LearningVocabularyLookupHistory> twoDayRecords = safeList(
                lookupHistoryMapper.selectSinceTime(userId, windowStart, MAX_RECENT_WINDOW_LIMIT));
        int recentTwoDayCount = twoDayRecords.size();

        RecentSearchReviewQueueResponse response = new RecentSearchReviewQueueResponse();
        response.setRecentTwoDayCount(recentTwoDayCount);

        if (recentTwoDayCount >= resolvedMinCount) {
            List<RecentSearchReviewItemResponse> items = assembleFromLookupHistories(userId, twoDayRecords);
            long unreviewedCount = twoDayRecords.stream()
                    .filter(r -> r.getLastReviewedTime() == null || r.getLastReviewedTime().isBefore(r.getLastLookupTime()))
                    .count();
            response.setMode("today_yesterday");
            response.setTotalCount(items.size());
            String hint;
            if (unreviewedCount > 0 && unreviewedCount < items.size()) {
                hint = "今天与昨天共搜索过 " + items.size() + " 个词，其中 " + unreviewedCount + " 个待复习已优先置顶";
            } else if (unreviewedCount == 0) {
                hint = "近两日搜索的 " + items.size() + " 个词已全部巩固过一遍，再次回看温故知新";
            } else {
                hint = "今天与昨天共搜索过 " + items.size() + " 个词，趁热巩固一遍";
            }
            response.setHintMessage(hint);
            response.setItems(items);
            return response;
        }

        List<LearningVocabularyLookupHistory> recentRecords = safeList(
                lookupHistoryMapper.selectRecent(userId, resolvedFallbackLimit));
        if (!recentRecords.isEmpty()) {
            List<RecentSearchReviewItemResponse> items = assembleFromLookupHistories(userId, recentRecords);
            long unreviewedCount = recentRecords.stream()
                    .filter(r -> r.getLastReviewedTime() == null || r.getLastReviewedTime().isBefore(r.getLastLookupTime()))
                    .count();
            response.setMode("recent_fallback");
            response.setTotalCount(items.size());
            String message;
            if (unreviewedCount > 0 && unreviewedCount < items.size()) {
                message = "已为你自动补齐最近搜索的 " + items.size() + " 个词，其中 " + unreviewedCount + " 个待复习已优先置顶";
            } else if (recentTwoDayCount > 0) {
                message = "今天与昨天仅搜索 " + recentTwoDayCount + " 个词，已为你自动补齐最近搜索的 " + items.size() + " 个词";
            } else {
                message = "近两日暂无新搜索记录，已为你展示最近搜索的 " + items.size() + " 个词";
            }
            response.setHintMessage(message);
            response.setItems(items);
            return response;
        }

        List<LearningWordbookEntry> fallbackEntries = safeList(wordbookEntryMapper.selectList(
                new LambdaQueryWrapper<LearningWordbookEntry>()
                        .eq(LearningWordbookEntry::getUserId, userId)
                        .orderByDesc(LearningWordbookEntry::getUpdateTime, LearningWordbookEntry::getId)
                        .last("LIMIT " + resolvedFallbackLimit)));
        List<RecentSearchReviewItemResponse> items = assembleFromWordbookEntries(fallbackEntries);
        response.setMode("wordbook_fallback");
        response.setTotalCount(items.size());
        response.setHintMessage(items.isEmpty()
                ? "还没有搜索过单词，去卡片学习或反刍中查几个词，这里就会自动生成轻量复习清单"
                : "暂无近期查词足迹，先为你展示最近更新的 " + items.size() + " 个词条（此后查过的词会自动汇入）");
        response.setItems(items);
        return response;
    }

    /**
     * 按归一化词汇查找用户个人词本中已存在的词条 ID（优先最近更新）。
     */
    public Long findOwnedEntryIdByTerm(Long userId, String term) {
        String normalized = normalizeTerm(term);
        if (userId == null || !StringUtils.hasText(normalized)) {
            return null;
        }
        LearningWordbookEntry entry = wordbookEntryMapper.selectOne(
                new LambdaQueryWrapper<LearningWordbookEntry>()
                        .eq(LearningWordbookEntry::getUserId, userId)
                        .eq(LearningWordbookEntry::getNormalizedTerm, normalized)
                        .orderByDesc(LearningWordbookEntry::getUpdateTime, LearningWordbookEntry::getId)
                        .last("LIMIT 1"));
        return entry != null ? entry.getId() : null;
    }

    /**
     * 标记某查词足迹已完成一次复习过关。
     */
    public void markReviewed(Long userId, String term) {
        String normalized = normalizeTerm(term);
        if (userId == null || !StringUtils.hasText(normalized)) {
            return;
        }
        try {
            lookupHistoryMapper.markReviewed(userId, normalized, LocalDateTime.now());
        } catch (RuntimeException ex) {
            log.debug("更新查词足迹复习时间失败 userId={} term={}", userId, normalized, ex);
        }
    }

    /**
     * 仅存在查词足迹、尚未加入单词本的词汇提交轻量复习结果：记录学习活跃度与业务日志，不强制创建词条。
     */
    public ReviewSubmitResponse submitLookupOnlyReview(Long userId, RecentSearchReviewSubmitRequest request) {
        ReviewResult result = ReviewResult.of(request.getResult());
        String normalized = normalizeTerm(request.getTerm());
        LocalDateTime now = LocalDateTime.now();
        markReviewed(userId, normalized);

        String idempotencyKey = "lookup_reviewed:" + userId + ":" + normalized + ":" + now.toLocalDate() + ":" + System.nanoTime();
        activityService.record(
                userId,
                LearningActivityEventType.WORD_REVIEWED,
                now,
                null,
                null,
                null,
                request.getLookupId(),
                1,
                request.getDurationSeconds(),
                result.getCode(),
                idempotencyKey
        );
        systemLogService.record(userId, SystemLogType.REVIEW, "完成近期查词复习",
                normalized + " -> " + result.getLabel());
        log.info("用户「{}」完成近期查词「{}」复习（未入词本），结果「{}」，方式「{}」",
                userId, normalized, result.getLabel(), request.getReviewAction());

        ReviewSubmitResponse response = new ReviewSubmitResponse();
        response.setEntryId(request.getLookupId());
        response.setNormalizedTerm(normalized);
        response.setReviewStage(result.remembered() ? 1 : 0);
        response.setMasteryScore(result.remembered() ? 70 : (result.vague() ? 45 : 20));
        response.setNextReviewTime(result.remembered() ? now.plusDays(1) : now.plusHours(4));
        return response;
    }

    private List<RecentSearchReviewItemResponse> assembleFromLookupHistories(
            Long userId, List<LearningVocabularyLookupHistory> histories) {
        if (histories.isEmpty()) {
            return List.of();
        }
        Set<Long> vocabIds = histories.stream()
                .map(LearningVocabularyLookupHistory::getVocabularyId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<String> normalizedTerms = histories.stream()
                .map(LearningVocabularyLookupHistory::getNormalizedTerm)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());

        Map<Long, EnglishVocabularyStudyRecord> vocabById = new LinkedHashMap<>();
        Map<String, EnglishVocabularyStudyRecord> vocabByTerm = new LinkedHashMap<>();
        loadVocabularyCards(vocabIds, normalizedTerms, vocabById, vocabByTerm);

        Map<String, LearningWordbookEntry> entryByTerm = loadUserEntriesByTerms(userId, normalizedTerms);

        List<RecentSearchReviewItemResponse> result = new ArrayList<>(histories.size());
        for (LearningVocabularyLookupHistory history : histories) {
            LearningWordbookEntry entry = entryByTerm.get(history.getNormalizedTerm());
            EnglishVocabularyStudyRecord card = history.getVocabularyId() != null
                    ? vocabById.get(history.getVocabularyId())
                    : null;
            if (card == null) {
                card = vocabByTerm.get(history.getNormalizedTerm());
            }

            RecentSearchReviewItemResponse item = new RecentSearchReviewItemResponse();
            item.setId(entry != null ? entry.getId() : history.getId());
            item.setLookupId(history.getId());
            item.setEntryId(entry != null ? entry.getId() : null);
            item.setWordbookId(entry != null ? entry.getWordbookId() : null);
            item.setVocabularyId(card != null ? card.getId() : history.getVocabularyId());
            item.setTerm(StringUtils.hasText(history.getTerm()) ? history.getTerm() : history.getNormalizedTerm());
            item.setNormalizedTerm(history.getNormalizedTerm());
            item.setLookupSource(history.getLookupSource());
            item.setLookupCount(history.getLookupCount() == null ? 1 : history.getLookupCount());
            item.setLastLookupTime(history.getLastLookupTime());
            item.setLastReviewedTime(history.getLastReviewedTime());
            item.setStatus(entry != null && StringUtils.hasText(entry.getStatus())
                    ? entry.getStatus()
                    : ReviewConstants.STATUS_VAGUE);
            item.setNote(entry != null ? entry.getNote() : null);
            String parsedJson = resolveParsedJson(card, entry);
            item.setParsed(parsedJson);
            result.add(item);
        }
        return result;
    }

    private List<RecentSearchReviewItemResponse> assembleFromWordbookEntries(List<LearningWordbookEntry> entries) {
        if (entries.isEmpty()) {
            return List.of();
        }
        Set<Long> vocabIds = entries.stream()
                .map(LearningWordbookEntry::getVocabularyId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<String> normalizedTerms = entries.stream()
                .map(LearningWordbookEntry::getNormalizedTerm)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());

        Map<Long, EnglishVocabularyStudyRecord> vocabById = new LinkedHashMap<>();
        Map<String, EnglishVocabularyStudyRecord> vocabByTerm = new LinkedHashMap<>();
        loadVocabularyCards(vocabIds, normalizedTerms, vocabById, vocabByTerm);

        List<RecentSearchReviewItemResponse> result = new ArrayList<>(entries.size());
        for (LearningWordbookEntry entry : entries) {
            EnglishVocabularyStudyRecord card = entry.getVocabularyId() != null
                    ? vocabById.get(entry.getVocabularyId())
                    : null;
            if (card == null) {
                card = vocabByTerm.get(entry.getNormalizedTerm());
            }
            RecentSearchReviewItemResponse item = new RecentSearchReviewItemResponse();
            item.setId(entry.getId());
            item.setLookupId(null);
            item.setEntryId(entry.getId());
            item.setWordbookId(entry.getWordbookId());
            item.setVocabularyId(card != null ? card.getId() : entry.getVocabularyId());
            item.setTerm(entry.getTerm());
            item.setNormalizedTerm(entry.getNormalizedTerm());
            item.setLookupSource(SOURCE_WORDBOOK_RECENT);
            item.setLookupCount(1);
            item.setLastLookupTime(entry.getUpdateTime() != null ? entry.getUpdateTime() : entry.getCreateTime());
            item.setLastReviewedTime(entry.getLastReviewTime());
            item.setStatus(StringUtils.hasText(entry.getStatus()) ? entry.getStatus() : ReviewConstants.STATUS_VAGUE);
            item.setNote(entry.getNote());
            item.setParsed(resolveParsedJson(card, entry));
            result.add(item);
        }
        return result;
    }

    private void loadVocabularyCards(
            Collection<Long> vocabIds,
            Collection<String> normalizedTerms,
            Map<Long, EnglishVocabularyStudyRecord> vocabById,
            Map<String, EnglishVocabularyStudyRecord> vocabByTerm) {
        if (!vocabIds.isEmpty()) {
            for (EnglishVocabularyStudyRecord card : safeList(studyRecordMapper.selectBatchIds(vocabIds))) {
                if (card == null || Boolean.TRUE.equals(card.getDeleted())) {
                    continue;
                }
                if (card.getId() != null) {
                    vocabById.put(card.getId(), card);
                }
                if (StringUtils.hasText(card.getNormalizedTerm())) {
                    vocabByTerm.putIfAbsent(card.getNormalizedTerm(), card);
                }
            }
        }
        List<String> missingTerms = normalizedTerms.stream()
                .filter(term -> !vocabByTerm.containsKey(term))
                .toList();
        if (!missingTerms.isEmpty()) {
            List<EnglishVocabularyStudyRecord> fetched = safeList(studyRecordMapper.selectList(
                    new LambdaQueryWrapper<EnglishVocabularyStudyRecord>()
                            .in(EnglishVocabularyStudyRecord::getNormalizedTerm, missingTerms)));
            for (EnglishVocabularyStudyRecord card : fetched) {
                if (card == null || Boolean.TRUE.equals(card.getDeleted())) {
                    continue;
                }
                if (card.getId() != null) {
                    vocabById.putIfAbsent(card.getId(), card);
                }
                if (StringUtils.hasText(card.getNormalizedTerm())) {
                    vocabByTerm.putIfAbsent(card.getNormalizedTerm(), card);
                }
            }
        }
    }

    private Map<String, LearningWordbookEntry> loadUserEntriesByTerms(Long userId, Collection<String> normalizedTerms) {
        if (userId == null || normalizedTerms.isEmpty()) {
            return Map.of();
        }
        List<LearningWordbookEntry> entries = safeList(wordbookEntryMapper.selectList(
                new LambdaQueryWrapper<LearningWordbookEntry>()
                        .eq(LearningWordbookEntry::getUserId, userId)
                        .in(LearningWordbookEntry::getNormalizedTerm, normalizedTerms)
                        .orderByDesc(LearningWordbookEntry::getUpdateTime, LearningWordbookEntry::getId)));
        Map<String, LearningWordbookEntry> map = new LinkedHashMap<>();
        for (LearningWordbookEntry entry : entries) {
            if (entry != null && StringUtils.hasText(entry.getNormalizedTerm())) {
                map.putIfAbsent(entry.getNormalizedTerm(), entry);
            }
        }
        return map;
    }

    private String resolveParsedJson(EnglishVocabularyStudyRecord card, LearningWordbookEntry entry) {
        if (card != null && StringUtils.hasText(card.getParsedJson())) {
            return card.getParsedJson();
        }
        if (entry != null && StringUtils.hasText(entry.getSnapshotParsedJson())) {
            return entry.getSnapshotParsedJson();
        }
        return null;
    }

    private String normalizeTerm(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        return raw.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private String normalizeSource(String source) {
        if (!StringUtils.hasText(source)) {
            return SOURCE_STUDY;
        }
        String normalized = source.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case SOURCE_QUICK_LOOKUP, SOURCE_WORDBOOK_ADD, SOURCE_STUDY -> normalized;
            default -> SOURCE_STUDY;
        };
    }

    private <T> List<T> safeList(List<T> items) {
        return items == null ? List.of() : items;
    }
}
