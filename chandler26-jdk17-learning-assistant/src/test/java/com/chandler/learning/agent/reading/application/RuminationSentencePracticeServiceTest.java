package com.chandler.learning.agent.reading.application;

import com.chandler.learning.agent.exception.LearningAssistantException;
import com.chandler.learning.agent.learning.application.LearningActivityService;
import com.chandler.learning.agent.learning.domain.enums.LearningActivityEventType;
import com.chandler.learning.agent.reading.api.request.RuminationSentencePracticeSubmitRequest;
import com.chandler.learning.agent.reading.api.response.RuminationMaterialResponse;
import com.chandler.learning.agent.reading.api.response.RuminationSentencePracticeSubmitResponse;
import com.chandler.learning.agent.reading.domain.entity.LearningRuminationMaterial;
import com.chandler.learning.agent.reading.domain.entity.LearningRuminationProbe;
import com.chandler.learning.agent.reading.domain.entity.LearningRuminationSentencePractice;
import com.chandler.learning.agent.reading.infrastructure.mapper.LearningRuminationMaterialMapper;
import com.chandler.learning.agent.reading.infrastructure.mapper.LearningRuminationProbeMapper;
import com.chandler.learning.agent.reading.infrastructure.mapper.LearningRuminationSentencePracticeMapper;
import com.chandler.learning.agent.system.application.SystemLogService;
import com.chandler.learning.agent.vocabulary.api.request.ReviewSubmitRequest;
import com.chandler.learning.agent.vocabulary.api.response.ReviewSubmitResponse;
import com.chandler.learning.agent.vocabulary.application.LearningWordProgressService;
import com.chandler.learning.agent.vocabulary.application.WordbookService;
import com.chandler.learning.agent.vocabulary.domain.entity.LearningWordProgress;
import com.chandler.learning.agent.vocabulary.domain.entity.LearningWordbook;
import com.chandler.learning.agent.vocabulary.domain.entity.LearningWordbookEntry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuminationSentencePracticeServiceTest {

    @Mock
    private WordbookService wordbookService;
    @Mock
    private LearningWordProgressService progressService;
    @Mock
    private LearningActivityService activityService;
    @Mock
    private SystemLogService systemLogService;
    @Mock
    private LearningRuminationMaterialMapper materialMapper;
    @Mock
    private LearningRuminationProbeMapper probeMapper;
    @Mock
    private LearningRuminationSentencePracticeMapper practiceMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private RuminationSentencePracticeService practiceService;

    private final Long userId = 1001L;
    private final Long wordbookId = 2001L;
    private final Long materialId = 3001L;

    private LearningWordbook wordbook;
    private LearningRuminationMaterial material;
    private LearningWordbookEntry entry;

    @BeforeEach
    void setUp() {
        practiceService = new RuminationSentencePracticeService(
                wordbookService, progressService, activityService, systemLogService,
                materialMapper, probeMapper, practiceMapper, objectMapper);

        wordbook = new LearningWordbook();
        wordbook.setId(wordbookId);
        wordbook.setUserId(userId);

        material = new LearningRuminationMaterial();
        material.setId(materialId);
        material.setUserId(userId);
        material.setWordbookId(wordbookId);
        material.setDeleted(false);
        material.setTargetWordsJson("[\"peel\",\"boil\"]");
        material.setArticleContent("First, start peeling the fresh carrots slowly. Then boil water in a pan.");

        entry = new LearningWordbookEntry();
        entry.setId(5001L);
        entry.setUserId(userId);
        entry.setWordbookId(wordbookId);
        entry.setTerm("peel");
        entry.setNormalizedTerm("peel");
    }

    @Test
    @DisplayName("原句跟敲完成后只增加练习次数与活动记录，绝不改写掌握度与复习排期")
    void copySentence_recordsPracticeCountOnlyWithoutReviewOrProgressChange() {
        when(wordbookService.requireOwnedWordbook(userId, wordbookId)).thenReturn(wordbook);
        when(materialMapper.selectById(materialId)).thenReturn(material);
        when(practiceMapper.selectByKeyForUpdate(userId, materialId, 0, "peel")).thenReturn(null);
        when(practiceMapper.selectByMaterialIds(userId, List.of(materialId))).thenReturn(List.of());

        RuminationSentencePracticeSubmitRequest req = new RuminationSentencePracticeSubmitRequest();
        req.setMaterialId(materialId);
        req.setWordbookId(wordbookId);
        req.setSentenceIndex(0);
        req.setTargetWord("peel");
        req.setPracticeMode("copy_sentence");
        req.setUserAnswer("first start peeling the fresh carrots slowly");
        req.setWrongKeyCount(2);
        req.setDurationMillis(6000L);

        RuminationSentencePracticeSubmitResponse res = practiceService.submitSentencePractice(userId, req);

        assertThat(res.getIsCorrect()).isTrue();
        assertThat(res.getCopyCount()).isEqualTo(1);
        assertThat(res.getMasterySettled()).isFalse();
        assertThat(res.getScheduleAdvanced()).isFalse();
        verify(wordbookService, never()).submitReview(any(), any(), any());
        verify(wordbookService, never()).recordRuminationClozeConsolidation(any(), any(), any());
        verify(progressService, never()).recordRuminationSpellingConsolidation(any(), any(), any(Boolean.class), any());
        verify(activityService).record(eq(userId), eq(LearningActivityEventType.RUMINATION_SENTENCE_PRACTICED),
                any(), any(), any(), eq(materialId), any(), eq(1), any(), eq("copy_sentence"), any());
    }

    @Test
    @DisplayName("使用查看原句提示或重试后通过遮挡补全时，不结算掌握度与复习排期")
    void clozeRecall_withHintOrRetry_recordsAssistedPracticeWithoutMasteryOrSchedule() {
        when(wordbookService.requireOwnedWordbook(userId, wordbookId)).thenReturn(wordbook);
        when(materialMapper.selectById(materialId)).thenReturn(material);
        LearningRuminationSentencePractice existing = existingRecord(0, "peel", "peeling");
        when(practiceMapper.selectByKeyForUpdate(userId, materialId, 0, "peel")).thenReturn(existing);
        when(practiceMapper.selectByMaterialIds(userId, List.of(materialId))).thenReturn(List.of(existing));

        RuminationSentencePracticeSubmitRequest req = new RuminationSentencePracticeSubmitRequest();
        req.setMaterialId(materialId);
        req.setWordbookId(wordbookId);
        req.setSentenceIndex(0);
        req.setTargetWord("peel");
        req.setPracticeMode("cloze_recall");
        req.setUserAnswer("peeling");
        req.setUsedHint(true);
        req.setAttemptCount(1);

        RuminationSentencePracticeSubmitResponse res = practiceService.submitSentencePractice(userId, req);

        assertThat(res.getIsCorrect()).isTrue();
        assertThat(res.getClozeIndependentPassed()).isFalse();
        assertThat(res.getMasterySettled()).isFalse();
        assertThat(res.getScheduleAdvanced()).isFalse();
        assertThat(existing.getClozeAssistedCount()).isEqualTo(1);
        verify(wordbookService, never()).submitReview(any(), any(), any());
        verify(progressService, never()).recordRuminationSpellingConsolidation(any(), any(), any(Boolean.class), any());
    }

    @Test
    @DisplayName("首次无提示独立遮挡补全且同篇探针未答对时，同步更新拼写掌握度与复习排期")
    void clozeRecall_cleanIndependentAndProbeNotPassed_settlesSpellingAndAdvancesSchedule() {
        when(wordbookService.requireOwnedWordbook(userId, wordbookId)).thenReturn(wordbook);
        when(materialMapper.selectById(materialId)).thenReturn(material);
        LearningRuminationSentencePractice existing = existingRecord(0, "peel", "peeling");
        when(practiceMapper.selectByKeyForUpdate(userId, materialId, 0, "peel")).thenReturn(existing);
        when(practiceMapper.countMasterySettledForTargetWord(userId, materialId, "peel")).thenReturn(0);
        when(wordbookService.findOwnedEntriesByTerms(userId, wordbookId, List.of("peel"))).thenReturn(List.of(entry));
        when(probeMapper.selectList(any())).thenReturn(List.of());

        ReviewSubmitResponse reviewOut = new ReviewSubmitResponse();
        reviewOut.setEntryId(entry.getId());
        reviewOut.setMasteryScore(75);
        reviewOut.setReviewStage(2);
        reviewOut.setNextReviewTime(LocalDateTime.now().plusDays(2));
        when(wordbookService.submitReview(eq(userId), eq(entry.getId()), any(ReviewSubmitRequest.class)))
                .thenReturn(reviewOut);

        LearningWordProgress progressOut = new LearningWordProgress();
        progressOut.setSpellingScore(30);
        when(progressService.recordRuminationSpellingConsolidation(eq(userId), eq("peel"), eq(true), any()))
                .thenReturn(progressOut);
        when(practiceMapper.selectByMaterialIds(userId, List.of(materialId))).thenReturn(List.of(existing));

        RuminationSentencePracticeSubmitRequest req = new RuminationSentencePracticeSubmitRequest();
        req.setMaterialId(materialId);
        req.setWordbookId(wordbookId);
        req.setSentenceIndex(0);
        req.setTargetWord("peel");
        req.setPracticeMode("cloze_recall");
        req.setUserAnswer("peeling");
        req.setUsedHint(false);
        req.setAttemptCount(1);

        RuminationSentencePracticeSubmitResponse res = practiceService.submitSentencePractice(userId, req);

        assertThat(res.getIsCorrect()).isTrue();
        assertThat(res.getClozeIndependentPassed()).isTrue();
        assertThat(res.getMasterySettled()).isTrue();
        assertThat(res.getScheduleAdvanced()).isTrue();
        assertThat(res.getSpellingScoreAfter()).isEqualTo(30);
        assertThat(res.getMasteryScoreAfter()).isEqualTo(75);
        ArgumentCaptor<ReviewSubmitRequest> captor = ArgumentCaptor.forClass(ReviewSubmitRequest.class);
        verify(wordbookService).submitReview(eq(userId), eq(entry.getId()), captor.capture());
        assertThat(captor.getValue().getAssessmentType()).isEqualTo("rumination_cloze");
    }

    @Test
    @DisplayName("首次无提示独立遮挡补全但同篇探针已答对时，不重复推迟复习排期仅更新拼写表现")
    void clozeRecall_cleanIndependentWhenProbeAlreadyPassed_updatesSpellingWithoutAdvancingSchedule() {
        when(wordbookService.requireOwnedWordbook(userId, wordbookId)).thenReturn(wordbook);
        when(materialMapper.selectById(materialId)).thenReturn(material);
        LearningRuminationSentencePractice existing = existingRecord(0, "peel", "peeling");
        when(practiceMapper.selectByKeyForUpdate(userId, materialId, 0, "peel")).thenReturn(existing);
        when(practiceMapper.countMasterySettledForTargetWord(userId, materialId, "peel")).thenReturn(0);
        when(wordbookService.findOwnedEntriesByTerms(userId, wordbookId, List.of("peel"))).thenReturn(List.of(entry));

        LearningRuminationProbe passedProbe = new LearningRuminationProbe();
        passedProbe.setTargetWord("peel");
        passedProbe.setIsCorrect(true);
        when(probeMapper.selectList(any())).thenReturn(List.of(passedProbe));

        ReviewSubmitResponse recordOut = new ReviewSubmitResponse();
        recordOut.setEntryId(entry.getId());
        recordOut.setMasteryScore(70);
        recordOut.setReviewStage(1);
        recordOut.setNextReviewTime(LocalDateTime.now().plusDays(1));
        when(wordbookService.recordRuminationClozeConsolidation(eq(userId), eq(entry.getId()), any()))
                .thenReturn(recordOut);

        LearningWordProgress progressOut = new LearningWordProgress();
        progressOut.setSpellingScore(15);
        when(progressService.recordRuminationSpellingConsolidation(eq(userId), eq("peel"), eq(true), any()))
                .thenReturn(progressOut);
        when(practiceMapper.selectByMaterialIds(userId, List.of(materialId))).thenReturn(List.of(existing));

        RuminationSentencePracticeSubmitRequest req = new RuminationSentencePracticeSubmitRequest();
        req.setMaterialId(materialId);
        req.setWordbookId(wordbookId);
        req.setSentenceIndex(0);
        req.setTargetWord("peel");
        req.setPracticeMode("cloze_recall");
        req.setUserAnswer("peeling");
        req.setUsedHint(false);
        req.setAttemptCount(1);

        RuminationSentencePracticeSubmitResponse res = practiceService.submitSentencePractice(userId, req);

        assertThat(res.getIsCorrect()).isTrue();
        assertThat(res.getMasterySettled()).isTrue();
        assertThat(res.getScheduleAdvanced()).isFalse();
        assertThat(res.getSpellingScoreAfter()).isEqualTo(15);
        verify(wordbookService, never()).submitReview(any(), any(), any());
        verify(wordbookService).recordRuminationClozeConsolidation(eq(userId), eq(entry.getId()), any());
    }

    @Test
    @DisplayName("列表批量装配应单次批量查询多篇材料巩固记录并填充统计")
    void enrichMaterialResponses_batchesQueriesAcrossMaterials() {
        RuminationMaterialResponse m1 = new RuminationMaterialResponse();
        m1.setId(String.valueOf(materialId));
        m1.setArticleContent("Start peeling potatoes.");
        m1.setTargetWords(List.of("peel"));

        LearningRuminationSentencePractice p1 = existingRecord(0, "peel", "peeling");
        p1.setCopyCount(2);
        p1.setClozeIndependentPassed(true);
        p1.setMasterySettled(true);
        when(practiceMapper.selectByMaterialIds(userId, List.of(materialId))).thenReturn(List.of(p1));

        practiceService.enrichMaterialResponses(userId, List.of(m1));

        verify(practiceMapper).selectByMaterialIds(userId, List.of(materialId));
        assertThat(m1.getPracticeSummary().getCopyCompletedSentences()).isEqualTo(1);
        assertThat(m1.getPracticeSummary().getClozePassedSentences()).isEqualTo(1);
        assertThat(m1.getPracticeSummary().getIndependentlySettledWords()).isEqualTo(1);
    }

    @Test
    @DisplayName("非法句子序号应拒绝提交")
    void submitSentencePractice_invalidSentenceIndex_throwsBadRequest() {
        when(wordbookService.requireOwnedWordbook(userId, wordbookId)).thenReturn(wordbook);
        when(materialMapper.selectById(materialId)).thenReturn(material);

        RuminationSentencePracticeSubmitRequest req = new RuminationSentencePracticeSubmitRequest();
        req.setMaterialId(materialId);
        req.setWordbookId(wordbookId);
        req.setSentenceIndex(99);
        req.setTargetWord("peel");
        req.setPracticeMode("copy_sentence");
        req.setUserAnswer("test");

        assertThatThrownBy(() -> practiceService.submitSentencePractice(userId, req))
                .isInstanceOf(LearningAssistantException.class)
                .hasMessageContaining("句子序号");
    }

    private LearningRuminationSentencePractice existingRecord(int sentenceIndex, String targetWord, String surfaceForm) {
        LearningRuminationSentencePractice rec = new LearningRuminationSentencePractice();
        rec.setId(7001L);
        rec.setUserId(userId);
        rec.setWordbookId(wordbookId);
        rec.setMaterialId(materialId);
        rec.setSentenceIndex(sentenceIndex);
        rec.setTargetWord(targetWord);
        rec.setSurfaceForm(surfaceForm);
        rec.setCopyCount(0);
        rec.setClozeAttemptCount(0);
        rec.setClozeAssistedCount(0);
        rec.setClozeIndependentPassed(false);
        rec.setMasterySettled(false);
        return rec;
    }
}
