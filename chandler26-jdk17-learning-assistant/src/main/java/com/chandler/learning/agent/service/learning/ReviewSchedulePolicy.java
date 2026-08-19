package com.chandler.learning.agent.service.learning;

import com.chandler.learning.agent.support.LearningConstants;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/** 根据复习结果计算下一次复习时间，并避开凌晨休息时段。 */
@Component
public class ReviewSchedulePolicy {

    public LocalDateTime nextReviewTime(LocalDateTime now, int stage, boolean remembered, boolean vague) {
        LocalDateTime baseTime = avoidSleepWindow(now);
        if (vague) {
            return avoidSleepWindow(baseTime.plusDays(LearningConstants.Review.VAGUE_REVIEW_DELAY_DAYS));
        }
        if (!remembered) {
            return addAwakeHours(baseTime, LearningConstants.Review.FORGOTTEN_REVIEW_DELAY_HOURS);
        }
        int resolvedStage = Math.max(LearningConstants.Review.INITIAL_STAGE,
                Math.min(stage, LearningConstants.Review.INTERVAL_DAYS.length - LearningConstants.SEQUENCE_STEP));
        return avoidSleepWindow(baseTime.plusDays(LearningConstants.Review.INTERVAL_DAYS[resolvedStage]));
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
}
