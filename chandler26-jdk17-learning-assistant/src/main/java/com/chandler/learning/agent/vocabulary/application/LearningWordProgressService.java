package com.chandler.learning.agent.vocabulary.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.chandler.learning.agent.vocabulary.domain.entity.LearningWordProgress;
import com.chandler.learning.agent.vocabulary.infrastructure.mapper.LearningWordProgressMapper;
import com.chandler.learning.agent.common.constant.CommonConstants;
import com.chandler.learning.agent.learning.domain.constant.ScenePlanConstants;
import com.chandler.learning.agent.vocabulary.domain.constant.ReviewConstants;
import com.chandler.learning.agent.vocabulary.domain.constant.VocabularyCardConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 维护用户跨自考、四六级和雅思词表共享的逐词学习进度。
 */
@Service
@RequiredArgsConstructor
public class LearningWordProgressService {

    private static final int WRITE_BATCH_SIZE = 200;

    private final LearningWordProgressMapper progressMapper;

    /**
     * 查询或创建一个全局逐词进度。
     */
    public LearningWordProgress getOrCreate(Long userId, String term, String masteryRequirement) {
        String normalizedTerm = normalize(term);
        LearningWordProgress existing = find(userId, normalizedTerm);
        if (existing != null) {
            if (ScenePlanConstants.MASTERY_SPELLING.equals(masteryRequirement)
                    && !ScenePlanConstants.MASTERY_SPELLING.equals(existing.getMasteryRequirement())) {
                existing.setMasteryRequirement(ScenePlanConstants.MASTERY_SPELLING);
                existing.setUpdateTime(LocalDateTime.now());
                progressMapper.updateById(existing);
            }
            return existing;
        }

        return createNew(userId, term, masteryRequirement);
    }

    /**
     * 场景曝光命令参数。
     */
    public record SceneExposureCommand(String term, String masteryRequirement, String tier, Long planId, Long unitId) {}

    /** 场景词进度的批量装配结果，保留写入前状态用于判断是否首次学习。 */
    public record SceneProgressBatch(Map<String, LearningWordProgress> progresses,
                                     Set<String> initiallyUnseenTerms) {}

    /**
     * 批量导入时一次预加载已有进度，只为真正缺失的归一化词插入新行。
     */
    public Map<String, LearningWordProgress> getOrCreateAll(Long userId, List<String> terms) {
        if (terms == null || terms.isEmpty()) {
            return new LinkedHashMap<>();
        }
        List<String> normalizedTerms = terms.stream()
                .filter(StringUtils::hasText)
                .map(this::normalize)
                .distinct()
                .toList();
        if (normalizedTerms.isEmpty()) {
            return new LinkedHashMap<>();
        }
        Map<String, LearningWordProgress> result = progressMapper.selectList(
                        new LambdaQueryWrapper<LearningWordProgress>()
                                .eq(LearningWordProgress::getUserId, userId)
                                .in(LearningWordProgress::getNormalizedTerm, normalizedTerms)
                                .eq(LearningWordProgress::getDeleted, false))
                .stream()
                .collect(Collectors.toMap(
                        LearningWordProgress::getNormalizedTerm,
                        progress -> progress,
                        (left, right) -> left,
                        LinkedHashMap::new));
        List<LearningWordProgress> missing = normalizedTerms.stream()
                .filter(normalizedTerm -> !result.containsKey(normalizedTerm))
                .map(normalizedTerm -> newProgress(userId, normalizedTerm,
                        ScenePlanConstants.MASTERY_RECOGNITION))
                .toList();
        for (int start = 0; start < missing.size(); start += WRITE_BATCH_SIZE) {
            int end = Math.min(start + WRITE_BATCH_SIZE, missing.size());
            List<LearningWordProgress> chunk = missing.subList(start, end);
            try {
                progressMapper.insertBatch(chunk);
                chunk.forEach(progress -> result.put(progress.getNormalizedTerm(), progress));
            } catch (DuplicateKeyException ex) {
                // 并发请求可能先创建了其中一部分，统一回读后继续装配，不逐条重试。
                List<LearningWordProgress> refreshed = findAll(userId, normalizedTerms);
                refreshed.forEach(progress -> result.put(progress.getNormalizedTerm(), progress));
                List<LearningWordProgress> unresolved = chunk.stream()
                        .filter(progress -> !result.containsKey(progress.getNormalizedTerm()))
                        .toList();
                if (!unresolved.isEmpty()) {
                    try {
                        progressMapper.insertBatch(unresolved);
                        unresolved.forEach(progress -> result.put(progress.getNormalizedTerm(), progress));
                    } catch (DuplicateKeyException retryConflict) {
                        findAll(userId, normalizedTerms).forEach(progress ->
                                result.put(progress.getNormalizedTerm(), progress));
                    }
                }
            }
        }
        return result;
    }

    private List<LearningWordProgress> findAll(Long userId, List<String> normalizedTerms) {
        return progressMapper.selectList(new LambdaQueryWrapper<LearningWordProgress>()
                .eq(LearningWordProgress::getUserId, userId)
                .in(LearningWordProgress::getNormalizedTerm, normalizedTerms)
                .eq(LearningWordProgress::getDeleted, false));
    }

    private LearningWordProgress createNew(Long userId, String term, String masteryRequirement) {
        LearningWordProgress progress = newProgress(userId, term, masteryRequirement);
        try {
            progressMapper.insert(progress);
            return progress;
        } catch (DuplicateKeyException ex) {
            return find(userId, progress.getNormalizedTerm());
        }
    }

    /** 在内存中构造进度实体，供单条和批量持久化复用。 */
    private LearningWordProgress newProgress(Long userId, String term, String masteryRequirement) {
        String normalizedTerm = normalize(term);
        LocalDateTime now = LocalDateTime.now();
        LearningWordProgress progress = new LearningWordProgress();
        progress.setId(IdWorker.getId());
        progress.setCreateBy(userId);
        progress.setUpdateBy(userId);
        progress.setUserId(userId);
        progress.setTerm(term == null ? normalizedTerm : term.trim());
        progress.setNormalizedTerm(normalizedTerm);
        progress.setLearningState(ScenePlanConstants.PROGRESS_UNSEEN);
        progress.setMasteryRequirement(resolveRequirement(masteryRequirement));
        progress.setRecognitionScore(CommonConstants.ZERO);
        progress.setRecognitionStage(CommonConstants.ZERO);
        progress.setRecognitionCorrectCount(CommonConstants.ZERO);
        progress.setRecognitionWrongCount(CommonConstants.ZERO);
        progress.setSpellingScore(CommonConstants.ZERO);
        progress.setSpellingStage(CommonConstants.ZERO);
        progress.setSpellingCorrectCount(CommonConstants.ZERO);
        progress.setSpellingWrongCount(CommonConstants.ZERO);
        progress.setExposureCount(CommonConstants.ZERO);
        progress.setSceneCount(CommonConstants.ZERO);
        progress.setCardStatus(VocabularyCardConstants.STATUS_NOT_REQUIRED);
        progress.setDeleted(false);
        progress.setCreateTime(now);
        progress.setUpdateTime(now);
        return progress;
    }

    /**
     * 批量记录词汇被一个新场景展示；核心词进入正式学习并按需等待词卡。
     */
    public void recordSceneExposures(Long userId, List<SceneExposureCommand> commands) {
        prepareSceneProgresses(userId, commands, true);
    }

    /**
     * 一次预加载并批量准备场景词进度；未来场景只提升掌握要求，开始学习的场景同时记录曝光。
     */
    public SceneProgressBatch prepareSceneProgresses(Long userId, List<SceneExposureCommand> commands,
                                                     boolean recordExposure) {
        if (commands == null || commands.isEmpty()) {
            return new SceneProgressBatch(Map.of(), Set.of());
        }
        List<String> terms = commands.stream()
                .map(SceneExposureCommand::term)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        Map<String, LearningWordProgress> progressMap = getOrCreateAll(userId, terms);
        Set<String> initiallyUnseenTerms = progressMap.values().stream()
                .filter(progress -> ScenePlanConstants.PROGRESS_UNSEEN.equals(progress.getLearningState())
                        || ScenePlanConstants.PROGRESS_EXPOSED.equals(progress.getLearningState()))
                .map(LearningWordProgress::getNormalizedTerm)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        LocalDateTime now = LocalDateTime.now();
        for (SceneExposureCommand cmd : commands) {
            String norm = normalize(cmd.term());
            LearningWordProgress progress = progressMap.get(norm);
            if (progress != null) {
                boolean core = ScenePlanConstants.TIER_CORE.equals(cmd.tier());
                progress.setTerm(cmd.term().trim());
                if (recordExposure) {
                    progress.setExposureCount(value(progress.getExposureCount()) + CommonConstants.SEQUENCE_STEP);
                    progress.setSceneCount(value(progress.getSceneCount()) + CommonConstants.SEQUENCE_STEP);
                    progress.setLatestPlanId(cmd.planId());
                    progress.setLatestUnitId(cmd.unitId());
                    progress.setLastLearnedTime(now);
                    if (core) {
                        if (!ScenePlanConstants.PROGRESS_MASTERED.equals(progress.getLearningState())) {
                            progress.setLearningState(ScenePlanConstants.PROGRESS_LEARNING);
                        }
                        if (VocabularyCardConstants.STATUS_NOT_REQUIRED.equals(progress.getCardStatus())) {
                            progress.setCardStatus(VocabularyCardConstants.STATUS_MISSING);
                        }
                    } else if (ScenePlanConstants.PROGRESS_UNSEEN.equals(progress.getLearningState())) {
                        progress.setLearningState(ScenePlanConstants.PROGRESS_EXPOSED);
                    }
                }
                if (ScenePlanConstants.MASTERY_SPELLING.equals(cmd.masteryRequirement())) {
                    progress.setMasteryRequirement(ScenePlanConstants.MASTERY_SPELLING);
                }
                progress.setUpdateTime(now);
                progress.setUpdateBy(userId);
            }
        }
        List<LearningWordProgress> toUpdate = progressMap.values().stream().filter(p -> p.getId() != null).toList();
        updateInChunks(toUpdate);
        return new SceneProgressBatch(Map.copyOf(progressMap), Set.copyOf(initiallyUnseenTerms));
    }

    /**
     * 记录词汇被一个新场景展示；核心词进入正式学习并按需等待词卡。
     */
    public LearningWordProgress recordSceneExposure(Long userId, String term, String masteryRequirement,
                                                     String tier, Long planId, Long unitId) {
        LearningWordProgress progress = getOrCreate(userId, term, masteryRequirement);
        boolean core = ScenePlanConstants.TIER_CORE.equals(tier);
        progress.setTerm(term.trim());
        progress.setExposureCount(value(progress.getExposureCount()) + CommonConstants.SEQUENCE_STEP);
        progress.setSceneCount(value(progress.getSceneCount()) + CommonConstants.SEQUENCE_STEP);
        progress.setLatestPlanId(planId);
        progress.setLatestUnitId(unitId);
        progress.setLastLearnedTime(LocalDateTime.now());
        if (core) {
            if (!ScenePlanConstants.PROGRESS_MASTERED.equals(progress.getLearningState())) {
                progress.setLearningState(ScenePlanConstants.PROGRESS_LEARNING);
            }
            if (VocabularyCardConstants.STATUS_NOT_REQUIRED.equals(progress.getCardStatus())) {
                progress.setCardStatus(VocabularyCardConstants.STATUS_MISSING);
            }
        } else if (ScenePlanConstants.PROGRESS_UNSEEN.equals(progress.getLearningState())) {
            progress.setLearningState(ScenePlanConstants.PROGRESS_EXPOSED);
        }
        if (ScenePlanConstants.MASTERY_SPELLING.equals(masteryRequirement)) {
            progress.setMasteryRequirement(ScenePlanConstants.MASTERY_SPELLING);
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
                userId, term, ScenePlanConstants.MASTERY_RECOGNITION);
        LocalDateTime now = LocalDateTime.now();
        progress.setTerm(term.trim());
        progress.setExposureCount(value(progress.getExposureCount()) + CommonConstants.SEQUENCE_STEP);
        progress.setLastLearnedTime(now);
        if (ScenePlanConstants.PROGRESS_UNSEEN.equals(progress.getLearningState())
                || ScenePlanConstants.PROGRESS_EXPOSED.equals(progress.getLearningState())) {
            progress.setLearningState(ScenePlanConstants.PROGRESS_LEARNING);
        }
        progress.setUpdateTime(now);
        progressMapper.updateById(progress);
        return progress;
    }

    /** 批量记录文章目标词曝光，避免精读完成时逐词更新数据库。 */
    public void recordArticleExposures(Long userId, List<String> terms) {
        if (terms == null || terms.isEmpty()) {
            return;
        }
        Map<String, LearningWordProgress> progressMap = getOrCreateAll(userId, terms);
        LocalDateTime now = LocalDateTime.now();
        progressMap.values().forEach(progress -> {
            progress.setExposureCount(value(progress.getExposureCount()) + CommonConstants.SEQUENCE_STEP);
            progress.setLastLearnedTime(now);
            if (ScenePlanConstants.PROGRESS_UNSEEN.equals(progress.getLearningState())
                    || ScenePlanConstants.PROGRESS_EXPOSED.equals(progress.getLearningState())) {
                progress.setLearningState(ScenePlanConstants.PROGRESS_LEARNING);
            }
            progress.setUpdateTime(now);
            progress.setUpdateBy(userId);
        });
        List<LearningWordProgress> updates = progressMap.values().stream().toList();
        updateInChunks(updates);
    }

    /** 按固定大小拆分批量更新，避免词汇任务生成过长 SQL。 */
    private void updateInChunks(List<LearningWordProgress> updates) {
        for (int start = 0; start < updates.size(); start += WRITE_BATCH_SIZE) {
            int end = Math.min(start + WRITE_BATCH_SIZE, updates.size());
            progressMapper.updateBatch(updates.subList(start, end));
        }
    }

    /**
     * 把一次检查结果归入认读或拼写维度。
     */
    public LearningWordProgress recordAssessment(Long progressId, String assessmentType, boolean correct,
                                                  LocalDateTime nextReviewTime) {
        LearningWordProgress progress = progressMapper.selectById(progressId);
        boolean spelling = ScenePlanConstants.ASSESSMENT_COPY_TYPING.equals(assessmentType)
                || ScenePlanConstants.ASSESSMENT_MEANING_SPELLING.equals(assessmentType);
        int delta = correct ? ReviewConstants.REMEMBERED_MASTERY_DELTA
                : -ReviewConstants.FORGOTTEN_MASTERY_DELTA;
        if (spelling) {
            progress.setSpellingScore(clamp(value(progress.getSpellingScore()) + delta));
            progress.setSpellingStage(correct ? value(progress.getSpellingStage()) + 1 : CommonConstants.ZERO);
            progress.setSpellingDueTime(nextReviewTime);
            progress.setSpellingCorrectCount(value(progress.getSpellingCorrectCount()) + (correct ? 1 : 0));
            progress.setSpellingWrongCount(value(progress.getSpellingWrongCount()) + (correct ? 0 : 1));
        } else {
            progress.setRecognitionScore(clamp(value(progress.getRecognitionScore()) + delta));
            progress.setRecognitionStage(correct ? value(progress.getRecognitionStage()) + 1 : CommonConstants.ZERO);
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
                .last(CommonConstants.SQL_LIMIT_ONE));
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
        boolean recognitionReady = value(progress.getRecognitionScore()) >= ScenePlanConstants.RECOGNITION_PASS_SCORE;
        boolean spellingReady = value(progress.getSpellingScore()) >= ScenePlanConstants.SPELLING_PASS_SCORE;
        if (recognitionReady && (!ScenePlanConstants.MASTERY_SPELLING.equals(progress.getMasteryRequirement()) || spellingReady)) {
            return ScenePlanConstants.PROGRESS_MASTERED;
        }
        return progress.getRecognitionStage() != null && progress.getRecognitionStage() > 0
                ? ScenePlanConstants.PROGRESS_REVIEWING
                : ScenePlanConstants.PROGRESS_LEARNING;
    }

    private String resolveRequirement(String requirement) {
        return ScenePlanConstants.MASTERY_SPELLING.equals(requirement)
                ? ScenePlanConstants.MASTERY_SPELLING
                : ScenePlanConstants.MASTERY_RECOGNITION;
    }

    private String normalize(String term) {
        return term == null ? "" : term.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private int value(Integer value) {
        return value == null ? CommonConstants.ZERO : value;
    }

    private int clamp(int value) {
        return Math.max(ReviewConstants.MIN_MASTERY, Math.min(value, ReviewConstants.MAX_MASTERY));
    }
}
