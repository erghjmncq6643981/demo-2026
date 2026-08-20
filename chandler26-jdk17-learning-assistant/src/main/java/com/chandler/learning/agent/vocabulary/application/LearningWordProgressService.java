package com.chandler.learning.agent.vocabulary.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chandler.learning.agent.vocabulary.domain.LearningWordProgress;
import com.chandler.learning.agent.vocabulary.infrastructure.LearningWordProgressMapper;
import com.chandler.learning.agent.support.LearningConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 维护用户跨自考、四六级和雅思词表共享的逐词学习进度。
 */
@Service
@RequiredArgsConstructor
public class LearningWordProgressService {

    private final LearningWordProgressMapper progressMapper;

    /**
     * 查询或创建一个全局逐词进度。
     */
    public LearningWordProgress getOrCreate(Long userId, String term, String masteryRequirement) {
        String normalizedTerm = normalize(term);
        LearningWordProgress existing = find(userId, normalizedTerm);
        if (existing != null) {
            if (LearningConstants.ScenePlan.MASTERY_SPELLING.equals(masteryRequirement)
                    && !LearningConstants.ScenePlan.MASTERY_SPELLING.equals(existing.getMasteryRequirement())) {
                existing.setMasteryRequirement(LearningConstants.ScenePlan.MASTERY_SPELLING);
                existing.setUpdateTime(LocalDateTime.now());
                progressMapper.updateById(existing);
            }
            return existing;
        }

        return createNew(userId, term, masteryRequirement);
    }

    /**
     * 批量导入时一次预加载已有进度，只为真正缺失的归一化词插入新行。
     */
    public Map<String, LearningWordProgress> getOrCreateAll(Long userId, List<String> terms) {
        Map<String, LearningWordProgress> result = progressMapper.selectList(
                        new LambdaQueryWrapper<LearningWordProgress>()
                                .eq(LearningWordProgress::getUserId, userId)
                                .eq(LearningWordProgress::getDeleted, false))
                .stream()
                .collect(Collectors.toMap(
                        LearningWordProgress::getNormalizedTerm,
                        progress -> progress,
                        (left, right) -> left,
                        LinkedHashMap::new));
        for (String term : terms) {
            String normalizedTerm = normalize(term);
            if (!result.containsKey(normalizedTerm)) {
                result.put(normalizedTerm, createNew(
                        userId, term, LearningConstants.ScenePlan.MASTERY_RECOGNITION));
            }
        }
        return result;
    }

    private LearningWordProgress createNew(Long userId, String term, String masteryRequirement) {
        String normalizedTerm = normalize(term);
        LocalDateTime now = LocalDateTime.now();
        LearningWordProgress progress = new LearningWordProgress();
        progress.setUserId(userId);
        progress.setTerm(term.trim());
        progress.setNormalizedTerm(normalizedTerm);
        progress.setLearningState(LearningConstants.ScenePlan.PROGRESS_UNSEEN);
        progress.setMasteryRequirement(resolveRequirement(masteryRequirement));
        progress.setRecognitionScore(LearningConstants.ZERO);
        progress.setRecognitionStage(LearningConstants.ZERO);
        progress.setRecognitionCorrectCount(LearningConstants.ZERO);
        progress.setRecognitionWrongCount(LearningConstants.ZERO);
        progress.setSpellingScore(LearningConstants.ZERO);
        progress.setSpellingStage(LearningConstants.ZERO);
        progress.setSpellingCorrectCount(LearningConstants.ZERO);
        progress.setSpellingWrongCount(LearningConstants.ZERO);
        progress.setExposureCount(LearningConstants.ZERO);
        progress.setSceneCount(LearningConstants.ZERO);
        progress.setCardStatus(LearningConstants.VocabularyCard.STATUS_NOT_REQUIRED);
        progress.setDeleted(false);
        progress.setCreateTime(now);
        progress.setUpdateTime(now);
        try {
            progressMapper.insert(progress);
            return progress;
        } catch (DuplicateKeyException ex) {
            return find(userId, normalizedTerm);
        }
    }

    /**
     * 记录词汇被一个新场景展示；核心词进入正式学习并按需等待词卡。
     */
    public LearningWordProgress recordSceneExposure(Long userId, String term, String masteryRequirement,
                                                     String tier, Long planId, Long unitId) {
        LearningWordProgress progress = getOrCreate(userId, term, masteryRequirement);
        boolean core = LearningConstants.ScenePlan.TIER_CORE.equals(tier);
        progress.setTerm(term.trim());
        progress.setExposureCount(value(progress.getExposureCount()) + LearningConstants.SEQUENCE_STEP);
        progress.setSceneCount(value(progress.getSceneCount()) + LearningConstants.SEQUENCE_STEP);
        progress.setLatestPlanId(planId);
        progress.setLatestUnitId(unitId);
        progress.setLastLearnedTime(LocalDateTime.now());
        if (core) {
            if (!LearningConstants.ScenePlan.PROGRESS_MASTERED.equals(progress.getLearningState())) {
                progress.setLearningState(LearningConstants.ScenePlan.PROGRESS_LEARNING);
            }
            if (LearningConstants.VocabularyCard.STATUS_NOT_REQUIRED.equals(progress.getCardStatus())) {
                progress.setCardStatus(LearningConstants.VocabularyCard.STATUS_MISSING);
            }
        } else if (LearningConstants.ScenePlan.PROGRESS_UNSEEN.equals(progress.getLearningState())) {
            progress.setLearningState(LearningConstants.ScenePlan.PROGRESS_EXPOSED);
        }
        if (LearningConstants.ScenePlan.MASTERY_SPELLING.equals(masteryRequirement)) {
            progress.setMasteryRequirement(LearningConstants.ScenePlan.MASTERY_SPELLING);
        }
        progress.setUpdateTime(LocalDateTime.now());
        progressMapper.updateById(progress);
        return progress;
    }

    /**
     * 记录单词在语境精读中完成了一次学习曝光，不增加场景学习次数。
     */
    public LearningWordProgress recordArticleExposure(Long userId, String term) {
        LearningWordProgress progress = getOrCreate(
                userId, term, LearningConstants.ScenePlan.MASTERY_RECOGNITION);
        LocalDateTime now = LocalDateTime.now();
        progress.setTerm(term.trim());
        progress.setExposureCount(value(progress.getExposureCount()) + LearningConstants.SEQUENCE_STEP);
        progress.setLastLearnedTime(now);
        if (LearningConstants.ScenePlan.PROGRESS_UNSEEN.equals(progress.getLearningState())
                || LearningConstants.ScenePlan.PROGRESS_EXPOSED.equals(progress.getLearningState())) {
            progress.setLearningState(LearningConstants.ScenePlan.PROGRESS_LEARNING);
        }
        progress.setUpdateTime(now);
        progressMapper.updateById(progress);
        return progress;
    }

    /**
     * 把一次检查结果归入认读或拼写维度。
     */
    public LearningWordProgress recordAssessment(Long progressId, String assessmentType, boolean correct,
                                                  LocalDateTime nextReviewTime) {
        LearningWordProgress progress = progressMapper.selectById(progressId);
        boolean spelling = LearningConstants.ScenePlan.ASSESSMENT_COPY_TYPING.equals(assessmentType)
                || LearningConstants.ScenePlan.ASSESSMENT_MEANING_SPELLING.equals(assessmentType);
        int delta = correct ? LearningConstants.Review.REMEMBERED_MASTERY_DELTA
                : -LearningConstants.Review.FORGOTTEN_MASTERY_DELTA;
        if (spelling) {
            progress.setSpellingScore(clamp(value(progress.getSpellingScore()) + delta));
            progress.setSpellingStage(correct ? value(progress.getSpellingStage()) + 1 : LearningConstants.ZERO);
            progress.setSpellingDueTime(nextReviewTime);
            progress.setSpellingCorrectCount(value(progress.getSpellingCorrectCount()) + (correct ? 1 : 0));
            progress.setSpellingWrongCount(value(progress.getSpellingWrongCount()) + (correct ? 0 : 1));
        } else {
            progress.setRecognitionScore(clamp(value(progress.getRecognitionScore()) + delta));
            progress.setRecognitionStage(correct ? value(progress.getRecognitionStage()) + 1 : LearningConstants.ZERO);
            progress.setRecognitionDueTime(nextReviewTime);
            progress.setRecognitionCorrectCount(value(progress.getRecognitionCorrectCount()) + (correct ? 1 : 0));
            progress.setRecognitionWrongCount(value(progress.getRecognitionWrongCount()) + (correct ? 0 : 1));
        }
        progress.setLearningState(resolveLearningState(progress));
        progress.setLastLearnedTime(LocalDateTime.now());
        progress.setUpdateTime(LocalDateTime.now());
        progressMapper.updateById(progress);
        return progress;
    }

    public LearningWordProgress find(Long userId, String normalizedTerm) {
        if (userId == null || !StringUtils.hasText(normalizedTerm)) {
            return null;
        }
        return progressMapper.selectOne(new LambdaQueryWrapper<LearningWordProgress>()
                .eq(LearningWordProgress::getUserId, userId)
                .eq(LearningWordProgress::getNormalizedTerm, normalizedTerm)
                .eq(LearningWordProgress::getDeleted, false)
                .last(LearningConstants.SQL_LIMIT_ONE));
    }

    /** 按进度主键批量查询，供其他业务域装配学习状态。 */
    public List<LearningWordProgress> findByIds(Collection<Long> progressIds) {
        return progressIds == null || progressIds.isEmpty() ? List.of() : progressMapper.selectBatchIds(progressIds);
    }

    /** 按用户和归一化词批量查询进度。 */
    public List<LearningWordProgress> findByTerms(Long userId, Collection<String> normalizedTerms) {
        if (userId == null || normalizedTerms == null || normalizedTerms.isEmpty()) {
            return List.of();
        }
        return progressMapper.selectList(new LambdaQueryWrapper<LearningWordProgress>()
                .eq(LearningWordProgress::getUserId, userId)
                .in(LearningWordProgress::getNormalizedTerm, normalizedTerms)
                .eq(LearningWordProgress::getDeleted, false));
    }

    /** 按进度主键查询。 */
    public LearningWordProgress findById(Long progressId) {
        return progressId == null ? null : progressMapper.selectById(progressId);
    }

    private String resolveLearningState(LearningWordProgress progress) {
        boolean recognitionReady = value(progress.getRecognitionScore()) >= LearningConstants.ScenePlan.RECOGNITION_PASS_SCORE;
        boolean spellingReady = value(progress.getSpellingScore()) >= LearningConstants.ScenePlan.SPELLING_PASS_SCORE;
        if (recognitionReady && (!LearningConstants.ScenePlan.MASTERY_SPELLING.equals(progress.getMasteryRequirement()) || spellingReady)) {
            return LearningConstants.ScenePlan.PROGRESS_MASTERED;
        }
        return progress.getRecognitionStage() != null && progress.getRecognitionStage() > 0
                ? LearningConstants.ScenePlan.PROGRESS_REVIEWING
                : LearningConstants.ScenePlan.PROGRESS_LEARNING;
    }

    private String resolveRequirement(String requirement) {
        return LearningConstants.ScenePlan.MASTERY_SPELLING.equals(requirement)
                ? LearningConstants.ScenePlan.MASTERY_SPELLING
                : LearningConstants.ScenePlan.MASTERY_RECOGNITION;
    }

    private String normalize(String term) {
        return term == null ? "" : term.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private int value(Integer value) {
        return value == null ? LearningConstants.ZERO : value;
    }

    private int clamp(int value) {
        return Math.max(LearningConstants.Review.MIN_MASTERY, Math.min(value, LearningConstants.Review.MAX_MASTERY));
    }
}
