package com.chandler.learning.agent.learning.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LearningPlanServiceWordSplitTest {

    @Test
    void splitsNinetySixWordsIntoThreeEvenMaterials() {
        assertThat(LearningPlanService.splitMaterialWordCounts(96))
                .containsExactly(32, 32, 32);
    }

    @Test
    void splitsEightyWordsIntoThreeMaterials() {
        assertThat(LearningPlanService.splitMaterialWordCounts(80))
                .containsExactly(27, 27, 26);
    }

    @Test
    void splitsOneHundredTwentyWordsIntoFourEvenMaterials() {
        assertThat(LearningPlanService.splitMaterialWordCounts(120))
                .containsExactly(30, 30, 30, 30);
    }

    @Test
    void keepsEachMaterialAtOrBelowThirtyFiveWords() {
        assertThat(LearningPlanService.splitMaterialWordCounts(121))
                .containsExactly(31, 30, 30, 30);
    }
}
