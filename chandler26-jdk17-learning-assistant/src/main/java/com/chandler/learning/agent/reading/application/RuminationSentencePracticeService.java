package com.chandler.learning.agent.reading.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chandler.learning.agent.common.exception.LearningErrorCode;
import com.chandler.learning.agent.exception.LearningAssistantException;
import com.chandler.learning.agent.learning.application.LearningActivityService;
import com.chandler.learning.agent.learning.domain.enums.LearningActivityEventType;
import com.chandler.learning.agent.reading.api.request.RuminationSentencePracticeSubmitRequest;
import com.chandler.learning.agent.reading.api.response.RuminationMaterialResponse;
import com.chandler.learning.agent.reading.api.response.RuminationPracticeSentenceResponse;
import com.chandler.learning.agent.reading.api.response.RuminationPracticeSummaryResponse;
import com.chandler.learning.agent.reading.api.response.RuminationSentencePracticeSubmitResponse;
import com.chandler.learning.agent.reading.api.response.RuminationSentenceTargetSpanResponse;
import com.chandler.learning.agent.reading.domain.entity.LearningRuminationMaterial;
import com.chandler.learning.agent.reading.domain.entity.LearningRuminationProbe;
import com.chandler.learning.agent.reading.domain.entity.LearningRuminationSentencePractice;
import com.chandler.learning.agent.reading.domain.enums.RuminationPracticeMode;
import com.chandler.learning.agent.reading.infrastructure.mapper.LearningRuminationMaterialMapper;
import com.chandler.learning.agent.reading.infrastructure.mapper.LearningRuminationProbeMapper;
import com.chandler.learning.agent.reading.infrastructure.mapper.LearningRuminationSentencePracticeMapper;
import com.chandler.learning.agent.system.application.SystemLogService;
import com.chandler.learning.agent.system.domain.enums.SystemLogType;
import com.chandler.learning.agent.vocabulary.api.request.AddWordbookEntryRequest;
import com.chandler.learning.agent.vocabulary.api.request.ReviewSubmitRequest;
import com.chandler.learning.agent.vocabulary.api.response.ReviewSubmitResponse;
import com.chandler.learning.agent.vocabulary.application.LearningWordProgressService;
import com.chandler.learning.agent.vocabulary.application.WordbookService;
import com.chandler.learning.agent.vocabulary.domain.entity.LearningWordProgress;
import com.chandler.learning.agent.vocabulary.domain.entity.LearningWordbook;
import com.chandler.learning.agent.vocabulary.domain.entity.LearningWordbookEntry;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 知识反刍阅读后按句跟敲与目标词遮挡补全服务。
 * <p>严格区分“练习次数”与“独立记忆表现”：原句跟敲与借提示通过不改变复习排期；
 * 仅首次无提示独立遮挡补全计入拼写掌握度，并避免与同篇材料微环境探针重复推迟下次复习时间。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RuminationSentencePracticeService {

    private static final String ASSESSMENT_RUMINATION_CLOZE = "rumination_cloze";

    private final WordbookService wordbookService;
    private final LearningWordProgressService progressService;
    private final LearningActivityService activityService;
    private final SystemLogService systemLogService;
    private final LearningRuminationMaterialMapper materialMapper;
    private final LearningRuminationProbeMapper probeMapper;
    private final LearningRuminationSentencePracticeMapper practiceMapper;
    private final ObjectMapper objectMapper;

    /**
     * 批量装配多篇场景材料的分句练习状态与汇总数据，消除列表页循环查库。
     */
    public void enrichMaterialResponses(Long userId, List<RuminationMaterialResponse> materials) {
        if (userId == null || materials == null || materials.isEmpty()) {
            return;
        }
        List<Long> materialIds = materials.stream()
                .map(RuminationMaterialResponse::getId)
                .filter(StringUtils::hasText)
                .map(this::parseIdSafely)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, List<LearningRuminationSentencePractice>> grouped = materialIds.isEmpty()
                ? Map.of()
                : practiceMapper.selectByMaterialIds(userId, materialIds).stream()
                .collect(Collectors.groupingBy(LearningRuminationSentencePractice::getMaterialId));

        for (RuminationMaterialResponse material : materials) {
            Long matId = parseIdSafely(material.getId());
            List<LearningRuminationSentencePractice> records = matId == null
                    ? List.of()
                    : grouped.getOrDefault(matId, List.of());
            RuminationSentenceExtractor.populatePracticeData(material, records);
        }
    }

    /**
     * 为单篇场景材料响应装配分句巩固数据与推荐列表。
     */
    public void enrichMaterialResponse(Long userId, RuminationMaterialResponse material) {
        if (material == null) {
            return;
        }
        enrichMaterialResponses(userId, List.of(material));
    }

    /**
     * 提交单句跟敲或目标词遮挡补全结果，按练习模式分流统计练习次数与掌握度结算。
     */
    @Transactional(rollbackFor = Exception.class)
    public RuminationSentencePracticeSubmitResponse submitSentencePractice(
            Long userId,
            RuminationSentencePracticeSubmitRequest request) {
        RuminationPracticeMode mode = RuminationPracticeMode.of(request.getPracticeMode());
        if (mode == null) {
            throw LearningAssistantException.badRequest(
                    LearningErrorCode.ARTICLE_WORDS_INVALID, "不支持的句型巩固模式");
        }

        LearningWordbook wordbook = wordbookService.requireOwnedWordbook(userId, request.getWordbookId());
        LearningRuminationMaterial material = materialMapper.selectById(request.getMaterialId());
        if (material == null || !userId.equals(material.getUserId()) || Boolean.TRUE.equals(material.getDeleted())) {
            throw LearningAssistantException.notFound(
                    LearningErrorCode.LEARNING_SCENE_MATERIAL_NOT_FOUND, "场景材料文章不存在或无权访问");
        }
        if (!wordbook.getId().equals(material.getWordbookId())) {
            throw LearningAssistantException.badRequest(
                    LearningErrorCode.ARTICLE_WORDS_INVALID, "场景材料与当前单词本不匹配");
        }

        List<String> targetWords = readTargetWords(material.getTargetWordsJson());
        List<RuminationSentenceExtractor.SplitSentence> sentences =
                RuminationSentenceExtractor.splitSentences(material.getArticleContent());
        RuminationSentenceExtractor.SplitSentence targetSentence = sentences.stream()
                .filter(s -> s.sentenceIndex() == request.getSentenceIndex())
                .findFirst()
                .orElseThrow(() -> LearningAssistantException.badRequest(
                        LearningErrorCode.ARTICLE_WORDS_INVALID, "请求的句子序号在当前材料中不存在"));

        String normalizedTargetWord = request.getTargetWord().trim().toLowerCase(Locale.ROOT);
        List<RuminationSentenceTargetSpanResponse> spans =
                RuminationSentenceExtractor.extractTargetSpans(targetSentence.text(), targetWords);
        RuminationSentenceTargetSpanResponse matchedSpan = spans.stream()
                .filter(span -> normalizedTargetWord.equalsIgnoreCase(span.getTargetWord()))
                .findFirst()
                .orElseThrow(() -> LearningAssistantException.badRequest(
                        LearningErrorCode.ARTICLE_WORDS_INVALID, "当前句子未包含目标词汇: " + normalizedTargetWord));

        String surfaceForm = matchedSpan.getSurfaceForm();
        LearningRuminationSentencePractice practiceRecord = lockOrCreatePracticeRecord(
                userId, wordbook.getId(), material.getId(),
                targetSentence.sentenceIndex(), normalizedTargetWord, surfaceForm, targetSentence.text());

        LocalDateTime now = LocalDateTime.now();
        return switch (mode) {
            case COPY_SENTENCE -> handleCopySentence(
                    userId, material, targetSentence, targetWords, practiceRecord, surfaceForm, request, now);
            case CLOZE_RECALL -> handleClozeRecall(
                    userId, wordbook.getId(), material, targetSentence, targetWords, practiceRecord,
                    surfaceForm, request, now);
        };
    }

    private RuminationSentencePracticeSubmitResponse handleCopySentence(
            Long userId,
            LearningRuminationMaterial material,
            RuminationSentenceExtractor.SplitSentence sentence,
            List<String> targetWords,
            LearningRuminationSentencePractice record,
            String surfaceForm,
            RuminationSentencePracticeSubmitRequest request,
            LocalDateTime now) {
        String expectedNormalized = normalizeSentenceTokens(sentence.text());
        String actualNormalized = normalizeSentenceTokens(request.getUserAnswer());
        boolean isCorrect = StringUtils.hasText(actualNormalized) && actualNormalized.equals(expectedNormalized);
        double accuracy = computeCopyAccuracy(expectedNormalized, actualNormalized, request.getWrongKeyCount(), isCorrect);

        if (isCorrect) {
            record.setCopyCount(value(record.getCopyCount()) + 1);
            record.setLastCopyAccuracy(accuracy);
        }
        record.setLastPracticedTime(now);
        record.setUpdateBy(userId);
        record.setUpdateTime(now);
        practiceMapper.updateById(record);

        if (isCorrect) {
            int durationSec = resolveDurationSeconds(request.getDurationMillis());
            activityService.record(userId, LearningActivityEventType.RUMINATION_SENTENCE_PRACTICED, now,
                    null, null, material.getId(), null, 1, durationSec,
                    "copy_sentence", "rumination_copy:" + record.getId() + ":" + UUID.randomUUID());
            systemLogService.record(userId, SystemLogType.REVIEW, "完成反刍原句跟敲",
                    record.getTargetWord() + " · sentence#" + record.getSentenceIndex());
            log.info("用户「{}」完成反刍原句跟敲 materialId={} sentenceIndex={} targetWord={} copyCount={} accuracy={}",
                    userId, material.getId(), record.getSentenceIndex(), record.getTargetWord(),
                    record.getCopyCount(), accuracy);
        }

        RuminationPracticeSummaryResponse summary = computeMaterialSummary(userId, material, targetWords);
        RuminationSentencePracticeSubmitResponse res = buildBaseResponse(
                material.getId(), sentence.sentenceIndex(), record.getTargetWord(), surfaceForm,
                RuminationPracticeMode.COPY_SENTENCE, isCorrect, isCorrect ? sentence.text() : surfaceForm,
                record, accuracy, false, false, false);
        res.setFeedbackMessage(isCorrect
                ? "原句跟敲已完成，已记入练习次数（不影响词条复习排期）"
                : "输入内容与原句尚有偏差，请对照高亮片段核对并听音频后重试");
        res.setPracticeSummary(summary);
        return res;
    }

    private RuminationSentencePracticeSubmitResponse handleClozeRecall(
            Long userId,
            Long wordbookId,
            LearningRuminationMaterial material,
            RuminationSentenceExtractor.SplitSentence sentence,
            List<String> targetWords,
            LearningRuminationSentencePractice record,
            String surfaceForm,
            RuminationSentencePracticeSubmitRequest request,
            LocalDateTime now) {
        String normalizedAnswer = collapseLower(request.getUserAnswer());
        String normalizedExpected = collapseLower(surfaceForm);
        boolean isCorrect = StringUtils.hasText(normalizedAnswer) && normalizedAnswer.equals(normalizedExpected);
        boolean usedHint = Boolean.TRUE.equals(request.getUsedHint());
        int attemptCount = request.getAttemptCount() == null || request.getAttemptCount() < 1
                ? 1 : request.getAttemptCount();
        double accuracy = computeWordAccuracy(normalizedExpected, normalizedAnswer, isCorrect);

        record.setClozeAttemptCount(value(record.getClozeAttemptCount()) + 1);
        record.setLastPracticedTime(now);
        record.setUpdateBy(userId);
        record.setUpdateTime(now);

        boolean cleanIndependentPass = isCorrect && !usedHint && attemptCount == 1;
        boolean settledThisCall = false;
        boolean scheduleAdvanced = false;
        Integer spellingScoreAfter = null;
        Integer masteryScoreAfter = null;
        Integer reviewStageAfter = null;
        LocalDateTime nextReviewTime = null;
        String feedbackMessage;

        if (!isCorrect) {
            practiceMapper.updateById(record);
            feedbackMessage = "目标词拼写尚未完全匹配当前语境词形（长度 " + surfaceForm.length()
                    + " 字符），请对照差异定位、查看原句或听音频后重试";
        } else if (!cleanIndependentPass) {
            record.setClozeAssistedCount(value(record.getClozeAssistedCount()) + 1);
            practiceMapper.updateById(record);
            recordPracticeActivity(userId, material.getId(), record.getId(), request.getDurationMillis(),
                    "cloze_assisted", now);
            feedbackMessage = "已完成本句遮挡巩固（因借助原句或重试通过，本次仅计入练习巩固，不提升掌握度与排期）";
            log.info("用户「{}」辅助完成反刍句型遮挡巩固 materialId={} targetWord={} usedHint={} attemptCount={}",
                    userId, material.getId(), record.getTargetWord(), usedHint, attemptCount);
        } else {
            record.setClozeIndependentPassed(true);
            boolean alreadySettledInMaterial = Boolean.TRUE.equals(record.getMasterySettled())
                    || practiceMapper.countMasterySettledForTargetWord(
                    userId, material.getId(), record.getTargetWord()) > 0;

            if (alreadySettledInMaterial) {
                practiceMapper.updateById(record);
                recordPracticeActivity(userId, material.getId(), record.getId(), request.getDurationMillis(),
                        "cloze_independent_repeat", now);
                feedbackMessage = "独立遮挡补全通过！该词已在本篇材料完成掌握度结算，本次计为巩固练习";
            } else {
                LearningWordbookEntry entry = ensureWordbookEntry(userId, wordbookId, record.getTargetWord());
                ReviewSubmitRequest reviewReq = buildClozeReviewRequest(
                        request.getUserAnswer().trim(), surfaceForm, request.getDurationMillis());
                boolean probeAlreadyPassed = hasProbeAlreadyPassed(userId, material.getId(), record.getTargetWord());

                if (!probeAlreadyPassed) {
                    ReviewSubmitResponse reviewOutcome = wordbookService.submitReview(userId, entry.getId(), reviewReq);
                    scheduleAdvanced = true;
                    masteryScoreAfter = reviewOutcome.getMasteryScore();
                    reviewStageAfter = reviewOutcome.getReviewStage();
                    nextReviewTime = reviewOutcome.getNextReviewTime();
                    feedbackMessage = "独立无提示一次拼写通过！已提升拼写掌握度并推进本次复习排期";
                } else {
                    ReviewSubmitResponse reviewOutcome =
                            wordbookService.recordRuminationClozeConsolidation(userId, entry.getId(), reviewReq);
                    scheduleAdvanced = false;
                    masteryScoreAfter = reviewOutcome.getMasteryScore();
                    reviewStageAfter = reviewOutcome.getReviewStage();
                    nextReviewTime = reviewOutcome.getNextReviewTime();
                    feedbackMessage = "独立无提示一次拼写通过！已提升拼写掌握度（同篇探针已推进排期，复习时间保持不变）";
                }

                LearningWordProgress progress = progressService.recordRuminationSpellingConsolidation(
                        userId, record.getTargetWord(), true, nextReviewTime);
                spellingScoreAfter = progress != null ? progress.getSpellingScore() : null;

                record.setMasterySettled(true);
                practiceMapper.updateById(record);
                settledThisCall = true;
                recordPracticeActivity(userId, material.getId(), record.getId(), request.getDurationMillis(),
                        "cloze_independent_pass", now);
                log.info("用户「{}」首次独立完成反刍句型遮挡补全 materialId={} targetWord={} scheduleAdvanced={} spellingScore={}",
                        userId, material.getId(), record.getTargetWord(), scheduleAdvanced, spellingScoreAfter);
            }
        }

        RuminationPracticeSummaryResponse summary = computeMaterialSummary(userId, material, targetWords);
        RuminationSentencePracticeSubmitResponse res = buildBaseResponse(
                material.getId(), sentence.sentenceIndex(), record.getTargetWord(), surfaceForm,
                RuminationPracticeMode.CLOZE_RECALL, isCorrect, surfaceForm,
                record, accuracy, usedHint, settledThisCall, scheduleAdvanced);
        res.setSpellingScoreAfter(spellingScoreAfter);
        res.setMasteryScoreAfter(masteryScoreAfter);
        res.setReviewStageAfter(reviewStageAfter);
        res.setNextReviewTime(nextReviewTime);
        res.setFeedbackMessage(feedbackMessage);
        res.setPracticeSummary(summary);
        return res;
    }

    private LearningRuminationSentencePractice lockOrCreatePracticeRecord(
            Long userId,
            Long wordbookId,
            Long materialId,
            int sentenceIndex,
            String targetWord,
            String surfaceForm,
            String sentenceText) {
        LearningRuminationSentencePractice existing = practiceMapper.selectByKeyForUpdate(
                userId, materialId, sentenceIndex, targetWord);
        if (existing != null) {
            return existing;
        }
        LearningRuminationSentencePractice created = new LearningRuminationSentencePractice();
        created.setUserId(userId);
        created.setWordbookId(wordbookId);
        created.setMaterialId(materialId);
        created.setSentenceIndex(sentenceIndex);
        created.setTargetWord(targetWord);
        created.setSurfaceForm(surfaceForm);
        created.setSentenceText(truncateSentenceSnapshot(sentenceText));
        created.setCopyCount(0);
        created.setClozeAttemptCount(0);
        created.setClozeAssistedCount(0);
        created.setClozeIndependentPassed(false);
        created.setMasterySettled(false);
        created.setCreateBy(userId);
        created.setUpdateBy(userId);
        try {
            practiceMapper.insert(created);
            return created;
        } catch (DuplicateKeyException ex) {
            LearningRuminationSentencePractice concurrent = practiceMapper.selectByKeyForUpdate(
                    userId, materialId, sentenceIndex, targetWord);
            if (concurrent != null) {
                return concurrent;
            }
            throw ex;
        }
    }

    private LearningWordbookEntry ensureWordbookEntry(Long userId, Long wordbookId, String targetWord) {
        List<LearningWordbookEntry> entries = wordbookService.findOwnedEntriesByTerms(
                userId, wordbookId, List.of(targetWord));
        if (entries.isEmpty()) {
            AddWordbookEntryRequest addReq = new AddWordbookEntryRequest();
            addReq.setTerm(targetWord);
            addReq.setNote("知识反刍场景拓展词汇");
            wordbookService.addEntry(userId, wordbookId, addReq);
            entries = wordbookService.findOwnedEntriesByTerms(userId, wordbookId, List.of(targetWord));
        }
        if (entries.isEmpty()) {
            throw LearningAssistantException.notFound(LearningErrorCode.ENTRY_NOT_FOUND, "单词本词条写入失败");
        }
        return entries.get(0);
    }

    private boolean hasProbeAlreadyPassed(Long userId, Long materialId, String targetWord) {
        List<LearningRuminationProbe> probes = probeMapper.selectList(
                new LambdaQueryWrapper<LearningRuminationProbe>()
                        .eq(LearningRuminationProbe::getMaterialId, materialId)
                        .eq(LearningRuminationProbe::getUserId, userId)
                        .eq(LearningRuminationProbe::getDeleted, 0));
        return probes.stream().anyMatch(probe -> probe != null
                && StringUtils.hasText(probe.getTargetWord())
                && targetWord.equalsIgnoreCase(probe.getTargetWord().trim())
                && Boolean.TRUE.equals(probe.getIsCorrect()));
    }

    private ReviewSubmitRequest buildClozeReviewRequest(String answer, String expectedForm, Long durationMillis) {
        ReviewSubmitRequest reviewReq = new ReviewSubmitRequest();
        reviewReq.setResult("remembered");
        reviewReq.setAssessmentType(ASSESSMENT_RUMINATION_CLOZE);
        reviewReq.setAnswerText(answer);
        reviewReq.setCorrectAnswer(expectedForm);
        reviewReq.setCheckResult("correct");
        reviewReq.setScore(100);
        reviewReq.setTypingAccuracy(100.0);
        reviewReq.setHintLevel(0);
        reviewReq.setAttemptCount(1);
        reviewReq.setDurationMillis(durationMillis);
        reviewReq.setDurationSeconds(resolveDurationSeconds(durationMillis));
        return reviewReq;
    }

    private void recordPracticeActivity(Long userId, Long materialId, Long practiceId, Long durationMillis,
                                        String resultCode, LocalDateTime now) {
        activityService.record(userId, LearningActivityEventType.RUMINATION_SENTENCE_PRACTICED, now,
                null, null, materialId, null, 1, resolveDurationSeconds(durationMillis),
                resultCode, "rumination_cloze:" + practiceId + ":" + UUID.randomUUID());
    }

    private RuminationPracticeSummaryResponse computeMaterialSummary(
            Long userId,
            LearningRuminationMaterial material,
            List<String> targetWords) {
        RuminationMaterialResponse temp = new RuminationMaterialResponse();
        temp.setId(String.valueOf(material.getId()));
        temp.setArticleContent(material.getArticleContent());
        temp.setTargetWords(targetWords);
        temp.setProbes(List.of());
        List<LearningRuminationSentencePractice> records = practiceMapper.selectByMaterialIds(
                userId, List.of(material.getId()));
        RuminationSentenceExtractor.populatePracticeData(temp, records);
        return temp.getPracticeSummary();
    }

    private RuminationSentencePracticeSubmitResponse buildBaseResponse(
            Long materialId,
            int sentenceIndex,
            String targetWord,
            String surfaceForm,
            RuminationPracticeMode mode,
            boolean isCorrect,
            String expectedSurfaceForm,
            LearningRuminationSentencePractice record,
            double accuracy,
            boolean usedHint,
            boolean masterySettled,
            boolean scheduleAdvanced) {
        RuminationSentencePracticeSubmitResponse res = new RuminationSentencePracticeSubmitResponse();
        res.setMaterialId(String.valueOf(materialId));
        res.setSentenceIndex(sentenceIndex);
        res.setTargetWord(targetWord);
        res.setSurfaceForm(surfaceForm);
        res.setPracticeMode(mode.getCode());
        res.setPracticeModeLabel(mode.getLabel());
        res.setIsCorrect(isCorrect);
        res.setExpectedSurfaceForm(expectedSurfaceForm);
        res.setCopyCount(value(record.getCopyCount()));
        res.setAccuracy(accuracy);
        res.setClozeAttemptCount(value(record.getClozeAttemptCount()));
        res.setClozeIndependentPassed(Boolean.TRUE.equals(record.getClozeIndependentPassed()));
        res.setUsedHint(usedHint);
        res.setMasterySettled(masterySettled);
        res.setScheduleAdvanced(scheduleAdvanced);
        return res;
    }

    private List<String> readTargetWords(String targetWordsJson) {
        if (!StringUtils.hasText(targetWordsJson)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(targetWordsJson,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private String normalizeSentenceTokens(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        return raw.toLowerCase(Locale.ROOT)
                .replaceAll("[’']", "'")
                .replaceAll("[^a-z0-9'\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String collapseLower(String raw) {
        return raw == null ? "" : raw.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private double computeCopyAccuracy(String expected, String actual, Integer wrongKeyCount, boolean isCorrect) {
        int len = Math.max(1, expected.length());
        int wrongKeys = Math.max(0, wrongKeyCount == null ? 0 : wrongKeyCount);
        if (isCorrect) {
            double ratio = Math.max(0, len - wrongKeys) * 100.0 / len;
            return Math.max(40.0, Math.round(ratio * 100.0) / 100.0);
        }
        int matched = commonPrefixLength(expected, actual);
        return Math.round((matched * 100.0 / len) * 100.0) / 100.0;
    }

    private double computeWordAccuracy(String expected, String actual, boolean isCorrect) {
        if (isCorrect) {
            return 100.0;
        }
        if (expected.isEmpty()) {
            return 0.0;
        }
        int matched = commonPrefixLength(expected, actual);
        return Math.round((matched * 100.0 / expected.length()) * 100.0) / 100.0;
    }

    private int commonPrefixLength(String left, String right) {
        int limit = Math.min(left.length(), right.length());
        int index = 0;
        while (index < limit && left.charAt(index) == right.charAt(index)) {
            index++;
        }
        return index;
    }

    private int resolveDurationSeconds(Long durationMillis) {
        if (durationMillis == null || durationMillis <= 0) {
            return 1;
        }
        return (int) Math.max(1L, Math.min(3600L, durationMillis / 1000L));
    }

    private String truncateSentenceSnapshot(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= 980 ? text : text.substring(0, 980);
    }

    private Long parseIdSafely(String rawId) {
        if (!StringUtils.hasText(rawId)) {
            return null;
        }
        try {
            return Long.parseLong(rawId.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private int value(Integer count) {
        return count == null ? 0 : count;
    }
}
