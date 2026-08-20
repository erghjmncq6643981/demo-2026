package com.chandler.learning.agent.learning.application;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class WordbookServiceScheduleTest {

    private final ReviewSchedulePolicy policy = new ReviewSchedulePolicy();

    @Test
    void avoidSleepWindowMovesNightTimeToSixOClock() {
        LocalDateTime result = policy.avoidSleepWindow(LocalDateTime.of(2026, 5, 19, 2, 30));

        assertThat(result).isEqualTo(LocalDateTime.of(2026, 5, 19, 6, 0));
    }

    @Test
    void addAwakeHoursSkipsMidnightToSixOClock() {
        LocalDateTime result = policy.addAwakeHours(LocalDateTime.of(2026, 5, 19, 23, 0), 4);

        assertThat(result).isEqualTo(LocalDateTime.of(2026, 5, 20, 9, 0));
    }

    @Test
    void addAwakeHoursStartsFromSixWhenSubmittedDuringSleepWindow() {
        LocalDateTime result = policy.addAwakeHours(LocalDateTime.of(2026, 5, 19, 2, 0), 4);

        assertThat(result).isEqualTo(LocalDateTime.of(2026, 5, 19, 10, 0));
    }
}
