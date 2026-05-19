package com.chandler.learning.agent.service.learning;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chandler.learning.agent.domain.dto.learning.AddWordbookEntryRequest;
import com.chandler.learning.agent.domain.dto.learning.ReviewSubmitRequest;
import com.chandler.learning.agent.domain.dto.learning.ReviewSubmitResponse;
import com.chandler.learning.agent.domain.dto.learning.WordbookEntryResponse;
import com.chandler.learning.agent.domain.dto.learning.WordbookResponse;
import com.chandler.learning.agent.domain.dto.learning.WordbookSaveRequest;
import com.chandler.learning.agent.domain.dto.vocabulary.VocabularyStudyRequest;
import com.chandler.learning.agent.domain.entity.learning.LearningReviewRecord;
import com.chandler.learning.agent.domain.entity.learning.LearningWordbook;
import com.chandler.learning.agent.domain.entity.learning.LearningWordbookEntry;
import com.chandler.learning.agent.domain.entity.vocabulary.EnglishVocabularyStudyRecord;
import com.chandler.learning.agent.mapper.learning.LearningReviewRecordMapper;
import com.chandler.learning.agent.mapper.learning.LearningWordbookEntryMapper;
import com.chandler.learning.agent.mapper.learning.LearningWordbookMapper;
import com.chandler.learning.agent.mapper.vocabulary.EnglishVocabularyStudyRecordMapper;
import com.chandler.learning.agent.service.vocabulary.EnglishVocabularyStudyService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class WordbookService {

    private static final int[] REVIEW_INTERVAL_DAYS = {0, 1, 2, 4, 7, 15, 30, 60};

    private final LearningWordbookMapper wordbookMapper;
    private final LearningWordbookEntryMapper entryMapper;
    private final LearningReviewRecordMapper reviewRecordMapper;
    private final EnglishVocabularyStudyRecordMapper vocabularyMapper;
    private final EnglishVocabularyStudyService vocabularyStudyService;
    private final VocabularyInsightService vocabularyInsightService;
    private final ObjectMapper objectMapper;

    public LearningWordbook ensureDefaultWordbook(Long userId) {
        LearningWordbook existing = wordbookMapper.selectOne(new LambdaQueryWrapper<LearningWordbook>()
                .eq(LearningWordbook::getUserId, userId)
                .eq(LearningWordbook::getIsDefault, true)
                .eq(LearningWordbook::getDeleted, false)
                .last("LIMIT 1"));
        if (existing != null) {
            return existing;
        }

        LocalDateTime now = LocalDateTime.now();
        LearningWordbook wordbook = new LearningWordbook();
        wordbook.setUserId(userId);
        wordbook.setName("默认词书");
        wordbook.setDescription("自动创建的英语词汇学习词书");
        wordbook.setIsDefault(true);
        wordbook.setDeleted(false);
        wordbook.setCreateTime(now);
        wordbook.setUpdateTime(now);
        wordbookMapper.insert(wordbook);
        return wordbook;
    }

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

    public WordbookResponse createWordbook(Long userId, WordbookSaveRequest request) {
        LocalDateTime now = LocalDateTime.now();
        if (Boolean.TRUE.equals(request.getIsDefault())) {
            clearDefault(userId);
        }

        LearningWordbook wordbook = new LearningWordbook();
        wordbook.setUserId(userId);
        wordbook.setName(request.getName().trim());
        wordbook.setDescription(trimToNull(request.getDescription()));
        wordbook.setIsDefault(Boolean.TRUE.equals(request.getIsDefault()));
        wordbook.setDeleted(false);
        wordbook.setCreateTime(now);
        wordbook.setUpdateTime(now);
        wordbookMapper.insert(wordbook);
        return toWordbookResponse(wordbook);
    }

    public WordbookEntryResponse addEntry(Long userId, Long wordbookId, AddWordbookEntryRequest request) {
        LearningWordbook wordbook = requireWordbook(userId, wordbookId);
        String normalizedTerm = normalize(request.getTerm());
        if (!StringUtils.hasText(normalizedTerm)) {
            throw new IllegalArgumentException("单词不能为空");
        }

        LearningWordbookEntry existing = entryMapper.selectOne(new LambdaQueryWrapper<LearningWordbookEntry>()
                .eq(LearningWordbookEntry::getWordbookId, wordbook.getId())
                .eq(LearningWordbookEntry::getNormalizedTerm, normalizedTerm)
                .last("LIMIT 1"));
        if (existing != null) {
            if (Boolean.TRUE.equals(existing.getDeleted())) {
                existing.setDeleted(false);
                existing.setNote(trimToNull(request.getNote()));
                existing.setUpdateTime(LocalDateTime.now());
                entryMapper.updateById(existing);
            }
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
            throw new IllegalArgumentException("词汇学习记录不存在: " + normalizedTerm);
        }

        LocalDateTime now = LocalDateTime.now();
        LearningWordbookEntry entry = new LearningWordbookEntry();
        entry.setUserId(userId);
        entry.setWordbookId(wordbook.getId());
        entry.setVocabularyId(vocabulary.getId());
        entry.setTerm(vocabulary.getTerm());
        entry.setNormalizedTerm(vocabulary.getNormalizedTerm());
        entry.setNote(trimToNull(request.getNote()));
        entry.setReviewStage(0);
        entry.setMasteryScore(0);
        entry.setNextReviewTime(now);
        entry.setDueCount(0);
        entry.setReviewCount(0);
        entry.setCorrectCount(0);
        entry.setWrongCount(0);
        entry.setDeleted(false);
        entry.setCreateTime(now);
        entry.setUpdateTime(now);
        entryMapper.insert(entry);
        return toEntryResponse(entry);
    }

    public List<WordbookEntryResponse> listEntries(Long userId, Long wordbookId, boolean dueOnly) {
        requireWordbook(userId, wordbookId);
        LambdaQueryWrapper<LearningWordbookEntry> wrapper = new LambdaQueryWrapper<LearningWordbookEntry>()
                .eq(LearningWordbookEntry::getUserId, userId)
                .eq(LearningWordbookEntry::getWordbookId, wordbookId)
                .eq(LearningWordbookEntry::getDeleted, false)
                .le(dueOnly, LearningWordbookEntry::getNextReviewTime, LocalDateTime.now())
                .orderByAsc(LearningWordbookEntry::getNextReviewTime)
                .orderByDesc(LearningWordbookEntry::getCreateTime);
        return entryMapper.selectList(wrapper).stream()
                .map(this::toEntryResponse)
                .toList();
    }

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
            entry.setDueCount(nullToZero(entry.getDueCount()) + 1);
            entry.setUpdateTime(LocalDateTime.now());
            entryMapper.updateById(entry);
        }
        return entries.stream().map(this::toEntryResponse).toList();
    }

    public ReviewSubmitResponse submitReview(Long userId, Long entryId, ReviewSubmitRequest request) {
        LearningWordbookEntry entry = entryMapper.selectById(entryId);
        if (entry == null || Boolean.TRUE.equals(entry.getDeleted()) || !entry.getUserId().equals(userId)) {
            throw new IllegalArgumentException("词书词条不存在: " + entryId);
        }
        String result = normalizeResult(request.getResult());
        LocalDateTime now = LocalDateTime.now();

        int stageBefore = nullToZero(entry.getReviewStage());
        int masteryBefore = nullToZero(entry.getMasteryScore());
        int stageAfter;
        int masteryAfter;
        boolean remembered = "remembered".equals(result);
        boolean vague = "vague".equals(result);
        if (remembered) {
            stageAfter = Math.min(stageBefore + 1, REVIEW_INTERVAL_DAYS.length - 1);
            masteryAfter = Math.min(100, masteryBefore + 15);
            entry.setCorrectCount(nullToZero(entry.getCorrectCount()) + 1);
        } else if (vague) {
            stageAfter = Math.max(1, stageBefore);
            masteryAfter = Math.max(0, Math.min(100, masteryBefore + 5));
        } else {
            stageAfter = 0;
            masteryAfter = Math.max(0, masteryBefore - 20);
            entry.setWrongCount(nullToZero(entry.getWrongCount()) + 1);
        }

        LocalDateTime nextReviewTime = nextReviewTime(now, stageAfter, remembered, vague);
        if (entry.getFirstReviewTime() == null) {
            entry.setFirstReviewTime(now);
        }
        entry.setLastReviewTime(now);
        entry.setNextReviewTime(nextReviewTime);
        entry.setReviewStage(stageAfter);
        entry.setMasteryScore(masteryAfter);
        entry.setReviewCount(nullToZero(entry.getReviewCount()) + 1);
        entry.setUpdateTime(now);
        entryMapper.updateById(entry);

        LearningReviewRecord record = new LearningReviewRecord();
        record.setUserId(userId);
        record.setWordbookId(entry.getWordbookId());
        record.setEntryId(entry.getId());
        record.setVocabularyId(entry.getVocabularyId());
        record.setNormalizedTerm(entry.getNormalizedTerm());
        record.setResult(result);
        record.setScore(request.getScore());
        record.setReviewStageBefore(stageBefore);
        record.setReviewStageAfter(stageAfter);
        record.setMasteryBefore(masteryBefore);
        record.setMasteryAfter(masteryAfter);
        record.setNextReviewTime(nextReviewTime);
        record.setDurationSeconds(request.getDurationSeconds());
        record.setCreateTime(now);
        reviewRecordMapper.insert(record);

        ReviewSubmitResponse response = new ReviewSubmitResponse();
        response.setEntryId(entry.getId());
        response.setNormalizedTerm(entry.getNormalizedTerm());
        response.setReviewStage(stageAfter);
        response.setMasteryScore(masteryAfter);
        response.setNextReviewTime(nextReviewTime);
        return response;
    }

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

    private WordbookEntryResponse toEntryResponse(LearningWordbookEntry entry) {
        WordbookEntryResponse response = new WordbookEntryResponse();
        response.setId(entry.getId());
        response.setWordbookId(entry.getWordbookId());
        response.setVocabularyId(entry.getVocabularyId());
        response.setTerm(entry.getTerm());
        response.setNormalizedTerm(entry.getNormalizedTerm());
        response.setNote(entry.getNote());
        response.setReviewStage(entry.getReviewStage());
        response.setMasteryScore(entry.getMasteryScore());
        response.setLastReviewTime(entry.getLastReviewTime());
        response.setNextReviewTime(entry.getNextReviewTime());
        response.setReviewCount(entry.getReviewCount());
        response.setCorrectCount(entry.getCorrectCount());
        response.setWrongCount(entry.getWrongCount());
        response.setParsed(readParsed(entry.getVocabularyId()));
        response.setTags(vocabularyInsightService.listTags(entry.getVocabularyId()));
        response.setRelations(vocabularyInsightService.listRelations(entry.getNormalizedTerm()));
        return response;
    }

    private Object readParsed(Long vocabularyId) {
        EnglishVocabularyStudyRecord record = vocabularyMapper.selectById(vocabularyId);
        if (record == null || !StringUtils.hasText(record.getParsedJson())) {
            return null;
        }
        try {
            return objectMapper.readValue(record.getParsedJson(), Object.class);
        } catch (Exception ex) {
            return null;
        }
    }

    private LearningWordbook requireWordbook(Long userId, Long wordbookId) {
        LearningWordbook wordbook = wordbookMapper.selectById(wordbookId);
        if (wordbook == null || Boolean.TRUE.equals(wordbook.getDeleted()) || !wordbook.getUserId().equals(userId)) {
            throw new IllegalArgumentException("词书不存在: " + wordbookId);
        }
        return wordbook;
    }

    private EnglishVocabularyStudyRecord findVocabulary(String normalizedTerm) {
        return vocabularyMapper.selectOne(new LambdaQueryWrapper<EnglishVocabularyStudyRecord>()
                .eq(EnglishVocabularyStudyRecord::getNormalizedTerm, normalizedTerm)
                .last("LIMIT 1"));
    }

    private void clearDefault(Long userId) {
        List<LearningWordbook> defaults = wordbookMapper.selectList(new LambdaQueryWrapper<LearningWordbook>()
                .eq(LearningWordbook::getUserId, userId)
                .eq(LearningWordbook::getIsDefault, true)
                .eq(LearningWordbook::getDeleted, false));
        for (LearningWordbook item : defaults) {
            item.setIsDefault(false);
            item.setUpdateTime(LocalDateTime.now());
            wordbookMapper.updateById(item);
        }
    }

    private LocalDateTime nextReviewTime(LocalDateTime now, int stage, boolean remembered, boolean vague) {
        if (vague) {
            return now.plusDays(1);
        }
        if (!remembered) {
            return now.plusHours(4);
        }
        return now.plusDays(REVIEW_INTERVAL_DAYS[Math.max(0, Math.min(stage, REVIEW_INTERVAL_DAYS.length - 1))]);
    }

    private String normalizeResult(String result) {
        String normalized = result == null ? "" : result.trim().toLowerCase(Locale.ROOT);
        if (List.of("remembered", "vague", "forgotten").contains(normalized)) {
            return normalized;
        }
        return "forgotten";
    }

    private String normalize(String term) {
        return term == null ? "" : term.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private int nullToZero(Integer value) {
        return value == null ? 0 : value;
    }
}
