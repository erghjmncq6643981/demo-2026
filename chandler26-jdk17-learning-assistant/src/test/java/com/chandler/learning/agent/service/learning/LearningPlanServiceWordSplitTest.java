package com.chandler.learning.agent.service.learning;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LearningPlanServiceWordSplitTest {

    @Test
    void splitsEightyWordsIntoTwoEvenMaterials() {
        assertThat(LearningPlanService.splitMaterialWordCounts(80))
                .containsExactly(40, 40);
    }

    @Test
    void splitsOneHundredTwentyWordsIntoThreeEvenMaterials() {
        assertThat(LearningPlanService.splitMaterialWordCounts(120))
                .containsExactly(40, 40, 40);
    }

    @Test
    void keepsEachMaterialAtOrBelowFiftyWords() {
        assertThat(LearningPlanService.splitMaterialWordCounts(121))
                .containsExactly(41, 40, 40);
    }
}
