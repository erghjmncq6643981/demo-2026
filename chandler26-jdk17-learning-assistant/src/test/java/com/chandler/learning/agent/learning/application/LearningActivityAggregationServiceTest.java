package com.chandler.learning.agent.learning.application;

import com.chandler.learning.agent.learning.domain.entity.LearningActivityDaily;
import com.chandler.learning.agent.learning.domain.entity.LearningActivityEvent;
import com.chandler.learning.agent.learning.domain.enums.LearningActivityEventType;
import com.chandler.learning.agent.learning.infrastructure.mapper.LearningActivityDailyMapper;
import com.chandler.learning.agent.learning.infrastructure.mapper.LearningActivityEventMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LearningActivityAggregationServiceTest {

    @Mock
    private LearningActivityEventMapper eventMapper;
    @Mock
    private LearningActivityDailyMapper dailyMapper;
    @Mock
    private LearningActivityService activityService;

    @Test
    void aggregatesClaimedEventsByUserDateAndMetricInOneBatch() {
        LearningActivityAggregationService service =
                new LearningActivityAggregationService(eventMapper, dailyMapper, activityService);
        LocalDateTime occurredAt = LocalDateTime.of(2026, 9, 9, 10, 30);
        LearningActivityEvent firstReview = event(
                1L, 1001L, LearningActivityEventType.WORD_REVIEWED, occurredAt, 1, 12, "correct");
        LearningActivityEvent secondReview = event(
                2L, 1001L, LearningActivityEventType.WORD_REVIEWED, occurredAt.plusMinutes(3), 2, 18, "forgotten");
        LearningActivityEvent sceneCompleted = event(
                3L, 1001L, LearningActivityEventType.SCENE_COMPLETED, occurredAt.plusMinutes(5), 1, null, null);

        when(eventMapper.claimPendingBatch(anyString(), eq(200))).thenReturn(3);
        when(eventMapper.selectByClaimToken(anyString()))
                .thenReturn(List.of(firstReview, secondReview, sceneCompleted));

        int processed = service.processPendingBatch(200);

        assertThat(processed).isEqualTo(3);
        ArgumentCaptor<Collection<LearningActivityDaily>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(dailyMapper).upsertBatch(captor.capture());
        assertThat(captor.getValue()).hasSize(2);

        LearningActivityDaily reviewMetric = metric(captor.getValue(), LearningActivityEventType.WORD_REVIEWED);
        assertThat(reviewMetric.getUserId()).isEqualTo(1001L);
        assertThat(reviewMetric.getActivityDate()).isEqualTo(LocalDate.of(2026, 9, 9));
        assertThat(reviewMetric.getMetricCount()).isEqualTo(3);
        assertThat(reviewMetric.getDurationSeconds()).isEqualTo(30);
        assertThat(reviewMetric.getCorrectCount()).isEqualTo(1);
        assertThat(reviewMetric.getTotalCount()).isEqualTo(3);

        LearningActivityDaily sceneMetric = metric(captor.getValue(), LearningActivityEventType.SCENE_COMPLETED);
        assertThat(sceneMetric.getMetricCount()).isEqualTo(1);
        verify(eventMapper).markSucceededByClaimToken(anyString());
    }

    @Test
    void createsAnEventWithAnExplicitSnowflakeIdForCustomXmlInsert() {
        LearningActivityEvent event = LearningActivityEvent.create(
                1001L, LearningActivityEventType.WORD_ADDED.getCode(),
                LocalDateTime.of(2026, 9, 9, 8, 0),
                null, null, null, 9001L, 1, null, "added", "word_added:9001");

        assertThat(event.getId()).isNotNull().isPositive();
        assertThat(event.getStatus()).isEqualTo("pending");
    }

    @Test
    void skipsProjectionWhenNoPendingEventCanBeClaimed() {
        LearningActivityAggregationService service =
                new LearningActivityAggregationService(eventMapper, dailyMapper, activityService);
        when(eventMapper.claimPendingBatch(anyString(), eq(200))).thenReturn(0);

        assertThat(service.processPendingBatch(200)).isZero();

        verify(eventMapper, never()).selectByClaimToken(anyString());
        verify(dailyMapper, never()).upsertBatch(org.mockito.ArgumentMatchers.any());
        verify(eventMapper, never()).markSucceededByClaimToken(anyString());
    }

    private LearningActivityEvent event(Long id, Long userId, LearningActivityEventType type,
                                        LocalDateTime occurredAt, Integer quantity,
                                        Integer durationSeconds, String resultCode) {
        LearningActivityEvent event = new LearningActivityEvent();
        event.setId(id);
        event.setUserId(userId);
        event.setEventType(type.getCode());
        event.setOccurredAt(occurredAt);
        event.setQuantity(quantity);
        event.setDurationSeconds(durationSeconds);
        event.setResultCode(resultCode);
        return event;
    }

    private LearningActivityDaily metric(Collection<LearningActivityDaily> metrics,
                                         LearningActivityEventType type) {
        return metrics.stream()
                .filter(item -> type.getCode().equals(item.getMetricType()))
                .findFirst()
                .orElseThrow();
    }
}
