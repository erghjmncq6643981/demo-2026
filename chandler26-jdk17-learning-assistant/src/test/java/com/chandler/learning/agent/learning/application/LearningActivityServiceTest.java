package com.chandler.learning.agent.learning.application;

import com.chandler.learning.agent.learning.api.response.LearningActivityDayResponse;
import com.chandler.learning.agent.learning.api.response.LearningActivityResponse;
import com.chandler.learning.agent.learning.domain.bo.LearningActivityMetricBO;
import com.chandler.learning.agent.learning.domain.enums.LearningActivityEventType;
import com.chandler.learning.agent.learning.infrastructure.mapper.LearningActivityDailyMapper;
import com.chandler.learning.agent.learning.infrastructure.mapper.LearningActivityEventMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LearningActivityServiceTest {

    @Mock
    private LearningActivityEventMapper eventMapper;
    @Mock
    private LearningActivityDailyMapper dailyMapper;

    private final Executor directExecutor = Runnable::run;

    @Test
    void aggregatesRuminationMetricsAndCalculatesStreaksCorrectly() {
        LearningActivityService service = new LearningActivityService(eventMapper, dailyMapper, directExecutor);
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        LocalDate twoDaysAgo = today.minusDays(2);
        LocalDate fiveDaysAgo = today.minusDays(5);
        LocalDate sixDaysAgo = today.minusDays(6);

        when(dailyMapper.selectMetrics(eq(1001L), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(
                        metric(sixDaysAgo, LearningActivityEventType.WORD_LEARNED, 3, 60),
                        metric(fiveDaysAgo, LearningActivityEventType.WORD_REVIEWED, 5, 45),
                        metric(twoDaysAgo, LearningActivityEventType.RUMINATION_SENTENCE_PRACTICED, 4, 120),
                        metric(twoDaysAgo, LearningActivityEventType.RUMINATION_PROBE_SUBMITTED, 2, 30),
                        metric(yesterday, LearningActivityEventType.RUMINATION_MATERIAL_GENERATED, 1, 0),
                        metric(yesterday, LearningActivityEventType.SCENE_COMPLETED, 1, 0),
                        metric(today, LearningActivityEventType.WORD_ADDED, 2, 0),
                        metric(today, LearningActivityEventType.SCENE_STARTED, 1, 15)
                ));

        LearningActivityResponse response = service.activity(1001L, 7);

        assertThat(response.getDays()).isEqualTo(7);
        assertThat(response.getLearnedTotal()).isEqualTo(3);
        assertThat(response.getReviewTotal()).isEqualTo(5);
        assertThat(response.getSceneCompletedTotal()).isEqualTo(1);
        assertThat(response.getWordAddedTotal()).isEqualTo(2);
        assertThat(response.getRuminationTotal()).isEqualTo(7); // 4 + 2 + 1
        assertThat(response.getTotalActivityCount()).isEqualTo(18); // 3 + 5 + 7 + 1 + 2 (SCENE_STARTED excluded)
        assertThat(response.getStudySecondsTotal()).isEqualTo(270);
        assertThat(response.getActiveDays()).isEqualTo(5);
        assertThat(response.getCurrentStreak()).isEqualTo(3); // twoDaysAgo, yesterday, today
        assertThat(response.getLongestStreak()).isEqualTo(3);

        LearningActivityDayResponse twoDaysAgoItem = findDay(response, twoDaysAgo);
        assertThat(twoDaysAgoItem.getRuminationCount()).isEqualTo(6);
        assertThat(twoDaysAgoItem.getTotalCount()).isEqualTo(6);
    }

    @Test
    void keepsCurrentStreakAliveWhenYesterdayWasActiveAndTodayIsStillZero() {
        LearningActivityService service = new LearningActivityService(eventMapper, dailyMapper, directExecutor);
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        LocalDate twoDaysAgo = today.minusDays(2);

        when(dailyMapper.selectMetrics(eq(2002L), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(
                        metric(twoDaysAgo, LearningActivityEventType.RUMINATION_SENTENCE_PRACTICED, 2, 30),
                        metric(yesterday, LearningActivityEventType.RUMINATION_PROBE_SUBMITTED, 3, 20)
                ));

        LearningActivityResponse response = service.activity(2002L, 7);

        assertThat(response.getActiveDays()).isEqualTo(2);
        assertThat(response.getCurrentStreak()).isEqualTo(2);
        assertThat(response.getLongestStreak()).isEqualTo(2);
        assertThat(findDay(response, today).getTotalCount()).isZero();
    }

    @Test
    void resetsCurrentStreakToZeroWhenBothTodayAndYesterdayAreInactive() {
        LearningActivityService service = new LearningActivityService(eventMapper, dailyMapper, directExecutor);
        LocalDate today = LocalDate.now();
        LocalDate threeDaysAgo = today.minusDays(3);
        LocalDate fourDaysAgo = today.minusDays(4);

        when(dailyMapper.selectMetrics(eq(3003L), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(
                        metric(fourDaysAgo, LearningActivityEventType.WORD_LEARNED, 4, 40),
                        metric(threeDaysAgo, LearningActivityEventType.WORD_LEARNED, 6, 50)
                ));

        LearningActivityResponse response = service.activity(3003L, 7);

        assertThat(response.getActiveDays()).isEqualTo(2);
        assertThat(response.getCurrentStreak()).isZero();
        assertThat(response.getLongestStreak()).isEqualTo(2);
    }

    private LearningActivityMetricBO metric(LocalDate date, LearningActivityEventType type, int count, int seconds) {
        LearningActivityMetricBO bo = new LearningActivityMetricBO();
        bo.setActivityDate(date);
        bo.setMetricType(type.getCode());
        bo.setMetricCount(count);
        bo.setDurationSeconds(seconds);
        return bo;
    }

    private LearningActivityDayResponse findDay(LearningActivityResponse response, LocalDate date) {
        String key = date.toString();
        return response.getItems().stream()
                .filter(item -> key.equals(item.getDate()))
                .findFirst()
                .orElseThrow();
    }
}
