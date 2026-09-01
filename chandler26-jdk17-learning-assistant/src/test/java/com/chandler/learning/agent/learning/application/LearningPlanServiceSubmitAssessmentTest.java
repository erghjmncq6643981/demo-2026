package com.chandler.learning.agent.learning.application;

import com.chandler.learning.agent.identity.application.UserDisplayNameService;
import com.chandler.learning.agent.learning.api.request.LearningAssessmentSubmitRequest;
import com.chandler.learning.agent.learning.api.response.LearningAssessmentSubmitResponse;
import com.chandler.learning.agent.learning.domain.bo.LearningAssessmentContextBO;
import com.chandler.learning.agent.learning.domain.constant.ScenePlanConstants;
import com.chandler.learning.agent.learning.domain.entity.LearningPlanUnit;
import com.chandler.learning.agent.learning.infrastructure.mapper.LearningPlanMapper;
import com.chandler.learning.agent.learning.infrastructure.mapper.LearningPlanUnitEntryMapper;
import com.chandler.learning.agent.learning.infrastructure.mapper.LearningPlanUnitMapper;
import com.chandler.learning.agent.learning.infrastructure.mapper.LearningReviewRecordMapper;
import com.chandler.learning.agent.system.application.SystemLogService;
import com.chandler.learning.agent.task.application.AiAsyncTaskService;
import com.chandler.learning.agent.task.application.AiTaskExecutionService;
import com.chandler.learning.agent.vocabulary.application.LearningWordProgressService;
import com.chandler.learning.agent.vocabulary.application.VocabularyCatalogQueryService;
import com.chandler.learning.agent.vocabulary.application.WordbookService;
import com.chandler.learning.agent.vocabulary.domain.entity.LearningWordProgress;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LearningPlanServiceSubmitAssessmentTest {

    @Mock
    private LearningPlanMapper planMapper;
    @Mock
    private LearningPlanUnitMapper unitMapper;
    @Mock
    private LearningPlanUnitEntryMapper unitEntryMapper;
    @Mock
    private LearningReviewRecordMapper reviewRecordMapper;
    @Mock
    private VocabularyCatalogQueryService catalogQueryService;
    @Mock
    private LearningWordProgressService progressService;
    @Mock
    private WordbookService wordbookService;
    @Mock
    private AiAsyncTaskService aiAsyncTaskService;
    @Mock
    private AiTaskExecutionService executionService;
    @Mock
    private SystemLogService systemLogService;
    @Mock
    private UserDisplayNameService userDisplayNameService;
    @Mock
    private LearningPlanResponseAssembler responseAssembler;
    @Mock
    private LearningPlanVocabularySelector vocabularySelector;
    @Mock
    private LearningPlanCalendarService calendarService;
    @Mock
    private LearningPlanLifecycleService lifecycleService;
    @Mock
    private LearningPlanSceneContentService sceneContentService;
    @Mock
    private LearningPlanScenePersistenceService scenePersistenceService;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private TransactionTemplate transactionTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ReviewSchedulePolicy reviewSchedulePolicy = new ReviewSchedulePolicy();
    private final LearningPlanAssessmentSupport assessmentSupport = new LearningPlanAssessmentSupport(objectMapper);

    private LearningPlanService service;

    @BeforeEach
    void setUp() {
        service = new LearningPlanService(
                planMapper,
                unitMapper,
                unitEntryMapper,
                reviewRecordMapper,
                catalogQueryService,
                progressService,
                wordbookService,
                reviewSchedulePolicy,
                aiAsyncTaskService,
                executionService,
                systemLogService,
                userDisplayNameService,
                responseAssembler,
                vocabularySelector,
                calendarService,
                lifecycleService,
                sceneContentService,
                assessmentSupport,
                scenePersistenceService,
                eventPublisher,
                objectMapper,
                transactionTemplate
        );
    }

    @Test
    void submitsMeaningChoiceAssessmentSuccessfullyWithEventPublishing() {
        Long userId = 1001L;
        Long planId = 2001L;
        Long unitId = 3001L;
        Long entryId = 4001L;
        Long wordbookEntryId = 5001L;
        Long progressId = 6001L;

        LearningAssessmentContextBO ctx = new LearningAssessmentContextBO();
        ctx.setPlanId(planId);
        ctx.setUserId(userId);
        ctx.setPlanName("场景英语");
        ctx.setWordbookId(10L);
        ctx.setUnitId(unitId);
        ctx.setUnitTitle("商务谈判");
        ctx.setCoreWordCount(10);
        ctx.setCompletedCoreCount(0);
        ctx.setUnitStatus("in_progress");
        ctx.setUnitEntryId(entryId);
        ctx.setTier(ScenePlanConstants.TIER_CORE);
        ctx.setMasteryRequirement(ScenePlanConstants.MASTERY_RECOGNITION);
        ctx.setTerm("negotiation");
        ctx.setNormalizedTerm("negotiation");
        ctx.setAssessmentJson("{\"correct_answer\":\"谈判，协商\"}");
        ctx.setFirstLearning(true);
        ctx.setWordbookEntryId(wordbookEntryId);
        ctx.setWordProgressId(progressId);
        ctx.setWordbookStage(0);
        ctx.setWordbookMastery(0);

        when(unitEntryMapper.selectAssessmentContext(userId, planId, unitId, entryId)).thenReturn(ctx);
        when(reviewRecordMapper.selectPassedAssessmentTypes(unitId, wordbookEntryId)).thenReturn(List.of());

        LearningWordProgress progress = new LearningWordProgress();
        progress.setId(progressId);
        progress.setLearningState("learned");
        progress.setRecognitionScore(70);
        progress.setSpellingScore(0);
        when(progressService.recordAssessment(eq(progressId), eq(ScenePlanConstants.ASSESSMENT_MEANING_CHOICE), eq(true), any())).thenReturn(progress);
        when(userDisplayNameService.userName(userId)).thenReturn("张三");

        LearningAssessmentSubmitRequest request = new LearningAssessmentSubmitRequest();
        request.setUnitEntryId(entryId);
        request.setAssessmentType(ScenePlanConstants.ASSESSMENT_MEANING_CHOICE);
        request.setAnswer("谈判，协商");
        request.setAttemptCount(1);
        request.setDurationMillis(2500L);

        LearningAssessmentSubmitResponse response = service.submitAssessment(userId, planId, unitId, request);

        assertThat(response).isNotNull();
        assertThat(response.getCorrect()).isTrue();
        assertThat(response.getCompletedCoreCount()).isEqualTo(1);
        assertThat(response.getRecognitionScore()).isEqualTo(70);

        ArgumentCaptor<LearningPlanUnit> unitCaptor = ArgumentCaptor.forClass(LearningPlanUnit.class);
        verify(unitMapper).updateById(unitCaptor.capture());
        assertThat(unitCaptor.getValue().getCompletedCoreCount()).isEqualTo(1);

        ArgumentCaptor<LearningAssessmentSubmittedEvent> eventCaptor = ArgumentCaptor.forClass(LearningAssessmentSubmittedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        LearningAssessmentSubmittedEvent event = eventCaptor.getValue();
        assertThat(event.userId()).isEqualTo(userId);
        assertThat(event.term()).isEqualTo("negotiation");
        assertThat(event.userName()).isEqualTo("张三");
    }
}
