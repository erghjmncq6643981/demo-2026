package com.chandler.learning.agent.reading.application;

import com.chandler.learning.agent.reading.api.response.RuminationMaterialResponse;
import com.chandler.learning.agent.reading.api.response.RuminationPracticeSentenceResponse;
import com.chandler.learning.agent.reading.api.response.RuminationProbeResponse;
import com.chandler.learning.agent.reading.api.response.RuminationSentenceTargetSpanResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RuminationSentenceExtractorTest {

    @Test
    @DisplayName("分句器应保护常见缩写与小数并保留段落归属")
    void splitSentences_preservesAbbreviationsDecimalsAndParagraphs() {
        String article = "Dr. Lin spent 2.5 hours peeling fresh potatoes, e.g. golden ones.\n\n"
                + "Wait for Mr. Chen! Then chop the onions carefully.";

        List<RuminationSentenceExtractor.SplitSentence> result =
                RuminationSentenceExtractor.splitSentences(article);

        assertThat(result).hasSize(3);
        assertThat(result.get(0).paragraphIndex()).isZero();
        assertThat(result.get(0).text())
                .isEqualTo("Dr. Lin spent 2.5 hours peeling fresh potatoes, e.g. golden ones.");
        assertThat(result.get(1).paragraphIndex()).isEqualTo(1);
        assertThat(result.get(1).text()).isEqualTo("Wait for Mr. Chen!");
        assertThat(result.get(2).paragraphIndex()).isEqualTo(1);
        assertThat(result.get(2).text()).isEqualTo("Then chop the onions carefully.");
    }

    @Test
    @DisplayName("目标词提取应短语优先不重叠并保留上下文词形变化")
    void extractTargetSpans_prefersLongerPhraseAndRetainsInflectedSurfaceForm() {
        String sentence = "Start peeling potatoes first, then whip smooth mashed potato for supper.";
        List<String> targets = List.of("potato", "mashed potato", "peel");

        List<RuminationSentenceTargetSpanResponse> spans =
                RuminationSentenceExtractor.extractTargetSpans(sentence, targets);

        assertThat(spans).extracting(
                RuminationSentenceTargetSpanResponse::getTargetWord,
                RuminationSentenceTargetSpanResponse::getSurfaceForm)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("peel", "peeling"),
                        org.assertj.core.groups.Tuple.tuple("potato", "potatoes"),
                        org.assertj.core.groups.Tuple.tuple("mashed potato", "mashed potato"));
    }

    @Test
    @DisplayName("默认重点句推荐应优先挑选探针答错词且去重目标词最多 3 句")
    void populatePracticeData_prioritizesWrongProbesAndCapsRecommendedAtThree() {
        RuminationMaterialResponse response = new RuminationMaterialResponse();
        response.setId("100");
        response.setTargetWords(List.of("peel", "chop", "boil", "crisp", "harvest"));
        response.setArticleContent(
                "First, peel each carrot slowly.\n"
                        + "Second, boil the water before adding salt.\n"
                        + "Third, chop the fresh herbs evenly.\n"
                        + "Finally, taste the crisp salad after harvest.");

        RuminationProbeResponse wrongBoilProbe = new RuminationProbeResponse();
        wrongBoilProbe.setTargetWord("boil");
        wrongBoilProbe.setIsCorrect(false);
        RuminationProbeResponse rightPeelProbe = new RuminationProbeResponse();
        rightPeelProbe.setTargetWord("peel");
        rightPeelProbe.setIsCorrect(true);
        response.setProbes(List.of(wrongBoilProbe, rightPeelProbe));

        RuminationSentenceExtractor.populatePracticeData(response, List.of());

        List<RuminationPracticeSentenceResponse> recommended = response.getPracticeSentences().stream()
                .filter(s -> Boolean.TRUE.equals(s.getRecommended()))
                .toList();
        assertThat(recommended).hasSize(3);
        assertThat(recommended).extracting(RuminationPracticeSentenceResponse::getPrimaryTargetWord)
                .contains("boil");
        assertThat(response.getPracticeSummary().getTotalTargetSentences()).isEqualTo(4);
        assertThat(response.getPracticeSummary().getRecommendedCount()).isEqualTo(3);
    }
}
