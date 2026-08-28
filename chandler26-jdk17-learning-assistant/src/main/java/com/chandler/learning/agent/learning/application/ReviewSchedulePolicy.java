package com.chandler.learning.agent.learning.application;

import com.chandler.learning.agent.common.constant.CommonConstants;
import com.chandler.learning.agent.vocabulary.domain.constant.ReviewConstants;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/** 根据复习结果计算下一次复习时间，并避开凌晨休息时段。 */
@Component
public class ReviewSchedulePolicy {

    public LocalDateTime nextReviewTime(LocalDateTime now, int stage, boolean remembered, boolean vague) {
        LocalDateTime baseTime = avoidSleepWindow(now);
        if (vague) {
            return avoidSleepWindow(baseTime.plusDays(ReviewConstants.VAGUE_REVIEW_DELAY_DAYS));
        }
        if (!remembered) {
            return addAwakeHours(baseTime, ReviewConstants.FORGOTTEN_REVIEW_DELAY_HOURS);
        }
        int resolvedStage = Math.max(ReviewConstants.INITIAL_STAGE,
                Math.min(stage, ReviewConstants.INTERVAL_DAYS.length - CommonConstants.SEQUENCE_STEP));
        return avoidSleepWindow(baseTime.plusDays(ReviewConstants.INTERVAL_DAYS[resolvedStage]));
    }

    LocalDateTime avoidSleepWindow(LocalDateTime reviewTime) {
        int hour = reviewTime.getHour();
        if (hour >= ReviewConstants.SLEEP_START_HOUR && hour < ReviewConstants.SLEEP_END_HOUR) {
            return reviewTime.toLocalDate().atTime(ReviewConstants.SLEEP_END_HOUR, CommonConstants.ZERO);
        }
        return reviewTime;
    }

    LocalDateTime addAwakeHours(LocalDateTime startTime, long hours) {
        LocalDateTime current = avoidSleepWindow(startTime);
        long remainingMinutes = hours * ChronoUnit.HOURS.getDuration().toMinutes();
        while (remainingMinutes > CommonConstants.ZERO) {
            LocalDateTime sleepStart = current.toLocalDate().atTime(
                    ReviewConstants.DAY_END_HOUR - CommonConstants.SEQUENCE_STEP,
                    CommonConstants.ZERO).plusHours(CommonConstants.SEQUENCE_STEP);
            long awakeMinutesToday = ChronoUnit.MINUTES.between(current, sleepStart);
            if (remainingMinutes <= awakeMinutesToday) {
                return avoidSleepWindow(current.plusMinutes(remainingMinutes));
            }
            remainingMinutes -= Math.max(awakeMinutesToday, CommonConstants.ZERO);
            current = current.toLocalDate().plusDays(CommonConstants.SEQUENCE_STEP)
                    .atTime(ReviewConstants.SLEEP_END_HOUR, CommonConstants.ZERO);
        }
        return avoidSleepWindow(current);
    }
}
